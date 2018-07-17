package com.oneindexed.spring.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringTemplateBean implements ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringTemplateBean.class);
    private static final String PROXY_PREFIX = "DMB";
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");

    private String template;
    private String propertyProvider;
    private String propertyRef;
    private boolean lazyInit = false;
    private boolean persistentDefinitions = false;
    private Set<Map<String, Object>> properties;
    private ApplicationContext context;
    private Set<Object> resolvedBeanDefinitions = new HashSet<>();
    private Set<String> allResolvedTemplates = new HashSet<>();
    private BeanDefinitionRegistry beanDefintionRegistry;
    private ConfigurableListableBeanFactory beanFactory;

    public void setTemplate(String template) {
        this.template = template;
    }

    public void setPropertyProvider(String propertyProvider) {
        this.propertyProvider = propertyProvider;
    }

    public void setPropertyRef(String propertyRef) {
        this.propertyRef = propertyRef;
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

    public void setPersistentDefinitions(boolean persistentDefinitions) {
        this.persistentDefinitions = persistentDefinitions;
    }

    public Set<String> getAllResolvedTemplates() {
        return allResolvedTemplates;
    }

    // TODO naming strategy for lazy init and persistent definitions
    public void init() {
        LOGGER.info("Creating spring template beans for {} from main context", template);

        retrieveProperties();

        int count = 0;

        for (Map<String, Object> currentConfig : properties) {
            count++;

            String thisPrefix = String.format("%s-%s-", PROXY_PREFIX, count);
            String implementingBean = String.format("%s%s", thisPrefix, template);

            // Replace the template bean and any dependencies which are configurable (with respect to this configuration)
            // with proxies. The template bean is always proxied itself
            addCustomBeanDefinitions(template, thisPrefix);

            // Resolve the properties on proxied beans with those from the configuration
            resolveBeanProperties(implementingBean, currentConfig);

            if (!lazyInit) {
                // Create bean
                LOGGER.info("Spring template bean created: {}", context.getBean(implementingBean));
            }

            // Reset and remove all proxy and related configuration from the context
            resetBeanProperties();

            // Track resolved template
            allResolvedTemplates.add(implementingBean);
        }

        LOGGER.info("Successfully resolved {} instances of template {} as {}", count, template, allResolvedTemplates);
    }

    private void retrieveProperties() {
        if (propertyRef != null) {
            properties = context.getBean(propertyRef, Set.class);
        } else if (propertyProvider != null ) {
            Object provider = context.getBean(propertyProvider);

            if (!SpringTemplatePropertyProvider.class.isAssignableFrom(provider.getClass())) {
                throw new BeanCreationException("property-provider " + propertyProvider + " must implement SpringTemplatePropertyProvider");
            }

            properties = ((SpringTemplatePropertyProvider)provider).getSpringTemplateProperties();
        } else {
            throw new BeanCreationException("Must define either property-ref or property-provider");
        }
    }

    private void addCustomBeanDefinitions(String bean, String proxyPrefix) {

        if (bean.startsWith(proxyPrefix)) {
            return;
        }

        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(bean);

        GenericBeanDefinition implBeanDefinition = new GenericBeanDefinition(beanDefinition);
        implBeanDefinition.setScope("prototype");

        MutablePropertyValues pvs = implBeanDefinition.getPropertyValues();

        for (PropertyValue pv : pvs.getPropertyValues()) {
            Object pVal = pv.getValue();

            if (RuntimeBeanReference.class.isAssignableFrom(pVal.getClass())) {
                RuntimeBeanReference rVal = (RuntimeBeanReference) pVal;

                if (!rVal.getBeanName().startsWith(proxyPrefix) && beanIsDmbConfigurable(rVal.getBeanName())) {
                    pvs.removePropertyValue(pv.getName());
                    pvs.addPropertyValue(new PropertyValue(pv.getName(), new RuntimeBeanReference(proxyPrefix + rVal.getBeanName())));

                    addCustomBeanDefinitions(rVal.getBeanName(), proxyPrefix);
                }
            }
        }

        Map<Integer, ConstructorArgumentValues.ValueHolder> cvs = beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues();

        for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cvs.entrySet()) {
            Object cVal = entry.getValue().getValue();
            Integer cIndex = entry.getKey();

            if (RuntimeBeanReference.class.isAssignableFrom(cVal.getClass())) {
                RuntimeBeanReference rVal = (RuntimeBeanReference) cVal;

                if (!rVal.getBeanName().startsWith(proxyPrefix) && beanIsDmbConfigurable(rVal.getBeanName())) {
                    cvs.get(cIndex).setValue(new RuntimeBeanReference(proxyPrefix + rVal.getBeanName()));

                    addCustomBeanDefinitions(rVal.getBeanName(), proxyPrefix);
                }
            }
        }

        beanDefintionRegistry.registerBeanDefinition(proxyPrefix + bean, implBeanDefinition);
    }

    private boolean beanIsDmbConfigurable(String bean) {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(bean);
        Map<String, Object> config = properties.iterator().next();

        for (PropertyValue v : beanDefinition.getPropertyValues().getPropertyValues()) {
            if (checkValueIsConfigurable(v.getValue(), config)) {
                return true;
            }
        }

        for (ConstructorArgumentValues.ValueHolder v :
                beanDefinition.getConstructorArgumentValues().getGenericArgumentValues()) {
            if (checkValueIsConfigurable(v.getValue(), config)) {
                return true;
            }
        }

        for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> v :
                beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues().entrySet()) {
            if (checkValueIsConfigurable(v.getValue().getValue(), config)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkValueIsConfigurable(Object value, Map<String, Object> config) {
        if (TypedStringValue.class.isAssignableFrom(value.getClass())) {
            String tsv = ((TypedStringValue)value).getValue();

            Matcher matcher = PROPERTY_PATTERN.matcher(tsv);

            while (matcher.find()) {
                if (config.containsKey(matcher.group(1))) {
                    return true;
                }
            }
        }

        return false;
    }

    private void resolveBeanProperties(String referenceBean, Map<String, Object> config) {

        BeanDefinition[] beanDefinitions = getBeanDefinition(referenceBean);

        for (BeanDefinition beanDefinition : beanDefinitions) {
            if (!resolvedBeanDefinitions.contains(beanDefinition)) {
                resolvedBeanDefinitions.add(beanDefinition);
                resolveBeanPropertyValues(referenceBean, beanDefinition, config);
                resolveBeanConstructorValues(referenceBean, beanDefinition, config);
            }
        }
    }

    private BeanDefinition[] getBeanDefinition(String bean) {
        BeanDefinition[] bd = new BeanDefinition[2];
        try {
            bd[0] = beanFactory.getMergedBeanDefinition(bean);
        } catch (NoSuchBeanDefinitionException e) {
            LOGGER.debug("No merged bean definition found for {}", bean);
        }
        try {
            bd[1] = beanFactory.getBeanDefinition(bean);
        } catch (NoSuchBeanDefinitionException e) {
            LOGGER.debug("No standard bean definition found for {}", bean);
        }

        if (bd[0] == null && bd[1] == null) {
            throw new NoSuchBeanDefinitionException("No BeanDefintion exists for " + bean);
        }

        return bd;
    }

    private void resolveBeanPropertyValues(String referenceBean, BeanDefinition beanDefinition,
                                           Map<String, Object> config) {

        MutablePropertyValues pvs = beanDefinition.getPropertyValues();

        for (PropertyValue pv : pvs.getPropertyValues()) {
            String pName = pv.getName();
            Object pVal = pv.getValue();

            if (RuntimeBeanReference.class.isAssignableFrom(pVal.getClass())) {
                RuntimeBeanReference rVal = (RuntimeBeanReference)pVal;
                resolveBeanProperties(rVal.getBeanName(), config);
            } else if (TypedStringValue.class.isAssignableFrom(pVal.getClass())) {
                String value = ((TypedStringValue) pVal).getValue();
                String adjustedValue =
                        resolveConfigurationString(referenceBean, value, config, beanDefinition.isPrototype());

                if (!value.equalsIgnoreCase(adjustedValue)) {
                    pvs.removePropertyValue(pName);
                    pvs.addPropertyValue(new PropertyValue(pName, new TypedStringValue(adjustedValue)));
                }
            }
        }
    }

    private void resolveBeanConstructorValues(String referenceBean, BeanDefinition beanDefinition,
                                              Map<String, Object> config) {

        Map<Integer, ConstructorArgumentValues.ValueHolder> cvs =
                beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues();

        for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cvs.entrySet()) {
            ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
            Object cVal = valueHolder.getValue();

            if (RuntimeBeanReference.class.isAssignableFrom(cVal.getClass())) {
                resolveBeanProperties(((RuntimeBeanReference) cVal).getBeanName(), config);
            } else if (TypedStringValue.class.isAssignableFrom(cVal.getClass())) {
                String value = ((TypedStringValue) cVal).getValue();
                String adjustedValue =
                        resolveConfigurationString(referenceBean, value, config, beanDefinition.isPrototype());

                if (!value.equalsIgnoreCase(adjustedValue)) {
                    valueHolder.setValue(new TypedStringValue(adjustedValue));
                }
            }
        }
    }

    private String resolveConfigurationString(String referenceBean, String unresolved, Map<String, Object> config,
                                              boolean isPrototype) {

        Matcher matcher = PROPERTY_PATTERN.matcher(unresolved);
        String adjustedValue = unresolved;

        while (matcher.find()) {
            String propValue = matcher.group(1);

            String replacement;
            if (config.containsKey(propValue)) {
                if (!isPrototype) {
                    throw new BeanCreationException("Spring template bean generation for prototype beans only: " + referenceBean);
                }
                replacement = (String) config.get(propValue);
            } else {
                replacement = beanFactory.getBean(propValue, String.class);
            }
            adjustedValue = adjustedValue.replaceAll("\\$\\{" + propValue + "}", replacement);
        }

        return adjustedValue;
    }

    private void resetBeanProperties() {
        resolvedBeanDefinitions.clear();

        if (!lazyInit && !persistentDefinitions) {
            for (String dmbBean : beanFactory.getBeanDefinitionNames()) {
                if (dmbBean.startsWith(PROXY_PREFIX)) {
                    beanDefintionRegistry.removeBeanDefinition(dmbBean);
                }
            }
        }
    }

    // TODO - trigger bean creation as part of normal bean processing or post processing
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
        this.beanDefintionRegistry = (BeanDefinitionRegistry)context.getAutowireCapableBeanFactory();
        this.beanFactory = ((AbstractApplicationContext)context).getBeanFactory();
        init();
    }
}

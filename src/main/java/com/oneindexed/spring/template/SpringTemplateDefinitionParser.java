package com.oneindexed.spring.template;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class SpringTemplateDefinitionParser extends AbstractBeanDefinitionParser {

    private static final String PROPERTY_REF = "property-ref";
    private static final String PROPERTY_PROVIDER = "property-provider";
    private static final List<String[]> ATTRIBUTES = new ArrayList<>();

    static {
        ATTRIBUTES.add(new String[] {"template", "template"});
        ATTRIBUTES.add(new String[] {PROPERTY_REF, "propertyRef"});
        ATTRIBUTES.add(new String[] {PROPERTY_PROVIDER, "propertyProvider"});
        ATTRIBUTES.add(new String[] {"lazy-init", "lazyInit"});
        ATTRIBUTES.add(new String[] {"persistent-definitions", "persistentDefinitions"});
    }

    @Nullable
    protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {

        BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .rootBeanDefinition(SpringTemplateBean.class);

        // Map from the xml style name to the java style name
        ATTRIBUTES.forEach(attribute -> {
            String attributeValue = element.getAttribute(attribute[0]);
            if (StringUtils.hasText(attributeValue)) {
                builder.addPropertyValue(attribute[1], attributeValue);
            }
        });

        // Fast fail
        if (!StringUtils.hasText(element.getAttribute(PROPERTY_REF)) &&
                (!StringUtils.hasText(element.getAttribute(PROPERTY_PROVIDER)))) {
            throw new BeanDefinitionValidationException(
                    "spring-template definition must contain property-ref or property-provider");
        }

        return builder.getBeanDefinition();
    }

    @Override
    protected boolean shouldGenerateId() {
        return true;
    }
}

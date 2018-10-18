package com.oneindexed.spring.template;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class SpringTemplateNamespaceHandler extends NamespaceHandlerSupport {

    public void init() {
        registerBeanDefinitionParser("spring-template", new SpringTemplateDefinitionParser());
    }
}

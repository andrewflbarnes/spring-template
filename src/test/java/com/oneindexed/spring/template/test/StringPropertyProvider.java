package com.oneindexed.spring.template.test;

import com.oneindexed.spring.template.SpringTemplatePropertyProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StringPropertyProvider implements SpringTemplatePropertyProvider {

    @Override
    public Set<Map<String, Object>> getSpringTemplateProperties() {
        Set<Map<String, Object>> propSet = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            Map<String, Object> propMap = new HashMap<>();
            propMap.put("propOne", String.valueOf(i));
            propMap.put("propTwo", String.valueOf(i));
            propMap.put("propSix", String.valueOf(i));

            propSet.add(propMap);
        }

        return propSet;
    }
}

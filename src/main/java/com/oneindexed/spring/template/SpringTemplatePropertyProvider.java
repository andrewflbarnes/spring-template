package com.oneindexed.spring.template;

import java.util.Map;
import java.util.Set;

public interface SpringTemplatePropertyProvider {

    Set<Map<String, Object>> getSpringTemplateProperties();
}

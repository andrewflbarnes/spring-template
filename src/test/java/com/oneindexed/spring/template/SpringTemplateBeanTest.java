package com.oneindexed.spring.template;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class SpringTemplateBeanTest {

    @Test
    public void testLoad() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:test.xml");

        SpringTemplateBean st = context.getBean(SpringTemplateBean.class);

        assertEquals(10, st.getAllResolvedTemplates().size());

        context.close();
    }

    @Test
    public void testPersistence() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:test-persist.xml");

        assertNotNull(context.getBean("ST-1-template"));

        context.close();
    }

    @Test(expected = NoSuchBeanDefinitionException.class)
    public void testNoPersistence() {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:test.xml");

        try {
            context.getBean("ST-1-template");
        } finally {
            context.close();
        }

        fail("When persistence is not set beans should not be available");
    }
}

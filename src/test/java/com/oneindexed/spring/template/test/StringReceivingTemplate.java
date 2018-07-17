package com.oneindexed.spring.template.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StringReceivingTemplate {

    private static final Logger LOGGER = LoggerFactory.getLogger(StringReceivingTemplate.class);

    private String propOne;
    private String propTwo;
    private String propThree;
    private String propFour;
    private String propFive;
    private String propSix;

    public void init() {
        LOGGER.info("properties are {}, {}, {}, {}, {} and {}",
                propOne, propTwo, propThree, propFour, propFive, propSix);
    }

    public void setPropOne(String propOne) {
        this.propOne = propOne;
    }

    public void setPropTwo(String propTwo) {
        this.propTwo = propTwo;
    }

    public void setPropThree(String propThree) {
        this.propThree = propThree;
    }

    public void setPropFour(String propFour) {
        this.propFour = propFour;
    }

    public void setPropFive(String propFive) {
        this.propFive = propFive;
    }

    public void setPropSix(String propSix) {
        this.propSix = propSix;
    }
}

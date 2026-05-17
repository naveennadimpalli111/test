//package com.myapp.config;
//
//import org.springframework.jms.core.JmsTemplate;
//import com.ibm.msg.client.wmq.WMQConstants;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
////import org.springframework.jms.core.JmsTemplate;
////import com.ibm.mq.jakarta.jms.MQConnectionFactory;
//import com.ibm.mq.jms.MQConnectionFactory;
//
//
//@Configuration
//
//public class MQConfig {
//
//@Bean
//    public MQConnectionFactory mqConnectionFactory() {
//        MQConnectionFactory factory = new MQConnectionFactory();
//        try {
//            factory.setHostName("localhost");
//            factory.setPort(1414);
//            factory.setQueueManager("QM1");
//            factory.setChannel("DEV.UR.BHMA");
//            factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
//
//            factory.setStringProperty(WMQConstants.USERID, "admin");
//            factory.setStringProperty(WMQConstants.PASSWORD, "passw0rd");
//
//        } catch (Exception e) {
//            throw new RuntimeException("Error creating MQ connection factory", e);
//        }
//        return factory;
//    }
//
//    @Bean
//    public JmsTemplate jmsTemplate(MQConnectionFactory factory) {
//       return new JmsTemplate(factory);
//    }
//
//
//	    }
//
//
//}

//package com.myapp.service;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jms.core.JmsTemplate;
//import org.springframework.stereotype.Service;
//
//@Service
//
//
//public class MQSender {
//
//@Autowired
//    private JmsTemplate jmsTemplate;
//
//    private static final String QUEUE_NAME = "RQ4L01";
//
//    public void sendMessage(String message) {
//        jmsTemplate.convertAndSend(QUEUE_NAME, message);
//        System.out.println("Message sent to MQ: " + message);
//    }
//
//
//}

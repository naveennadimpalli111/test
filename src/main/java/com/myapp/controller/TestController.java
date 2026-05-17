//package com.myapp.controller;
//
//
//import com.myapp.service.MQSender;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//
//
//public class TestController {
//
//	 @Autowired
//	    private MQSender mqSender;
//
//	    @GetMapping("/send")
//	    public String send(@RequestParam String message) {
//	        mqSender.sendMessage(message);
//	        return "Message sent to IBM MQ";
//	    }
//
//
//}

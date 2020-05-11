package com.ibm.hello.model.kafka.simple;

public class SimpleKafkaProducerResponse {
	public SimpleKafkaProducerResponse(int successCounter, int failCounter) {
		super();
		this.successCounter = successCounter;
		this.failCounter = failCounter;
	}
	int successCounter = 0;
	int failCounter = 0;
	
	public int getSuccessCounter() {
		return successCounter;
	}
	public int getFailCounter() {
		return failCounter;
	}
}

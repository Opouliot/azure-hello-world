package com.vanderlande.bushelloworld.azurebushelloworld;


import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusException;
import com.azure.messaging.servicebus.ServiceBusFailureReason;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.ServiceBusSenderClient;

@SpringBootApplication
public class AzureBusHelloWorldApplication {


    static String queueName = "teamsmsg";

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(AzureBusHelloWorldApplication.class, args);
        sendMessage();
        receiveMessages();
    }

    static void sendMessage() {
        // create a token using the default Azure credential
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
            .build();

        ServiceBusSenderClient senderClient = new ServiceBusClientBuilder()
            .fullyQualifiedNamespace("teamsalert.servicebus.windows.net")
            .credential(credential)
            .sender()
            .queueName(queueName)
            .buildClient();

        // send one message to the queue
        senderClient.sendMessage(new ServiceBusMessage("Hello, World!"));
        System.out.println("Sent a single message to the queue: " + queueName);
    }

    // handles received messages
    static void receiveMessages() throws InterruptedException {
        DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
                .build();

        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace("teamsalert.servicebus.windows.net")
                .credential(credential)
                .processor()
                .queueName(queueName)
                .processMessage(context -> processMessage(context))
                .processError(context -> processError(context))
                .buildProcessorClient();

        System.out.println("Starting the processor");
        processorClient.start();

        TimeUnit.SECONDS.sleep(10);
        System.out.println("Stopping and closing the processor");
        processorClient.close();
    }


    private static void processMessage(ServiceBusReceivedMessageContext context) {
        ServiceBusReceivedMessage message = context.getMessage();
        System.out.printf("Processing message. Session: %s, Sequence #: %s. Contents: %s%n", message.getMessageId(),
            message.getSequenceNumber(), message.getBody());
    }


    private static void processError(ServiceBusErrorContext context) {
        System.out.printf("Error when receiving messages from namespace: '%s'. Entity: '%s'%n",
            context.getFullyQualifiedNamespace(), context.getEntityPath());
    
        if (!(context.getException() instanceof ServiceBusException)) {
            System.out.printf("Non-ServiceBusException occurred: %s%n", context.getException());
            return;
        }
    
        ServiceBusException exception = (ServiceBusException) context.getException();
        ServiceBusFailureReason reason = exception.getReason();
    
        if (reason == ServiceBusFailureReason.MESSAGING_ENTITY_DISABLED
            || reason == ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND
            || reason == ServiceBusFailureReason.UNAUTHORIZED) {
            System.out.printf("An unrecoverable error occurred. Stopping processing with reason %s: %s%n",
                reason, exception.getMessage());
        } else if (reason == ServiceBusFailureReason.MESSAGE_LOCK_LOST) {
            System.out.printf("Message lock lost for message: %s%n", context.getException());
        } else if (reason == ServiceBusFailureReason.SERVICE_BUSY) {
            try {
                // Choosing an arbitrary amount of time to wait until trying again.
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                System.err.println("Unable to sleep for period of time");
            }
        } else {
            System.out.printf("Error source %s, reason %s, message: %s%n", context.getErrorSource(),
                reason, context.getException());
        }
    }

}

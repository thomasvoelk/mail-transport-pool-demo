package com.example.demo;

import org.apache.commons.pool2.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.mail.Address;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

class PooledJavaMailSender extends JavaMailSenderImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(PooledJavaMailSender.class);

    private static final String HEADER_MESSAGE_ID = "Message-ID";
    private ObjectPool<Transport> transportPool;

    PooledJavaMailSender(ObjectPool<Transport> transportPool) {
        this.transportPool = transportPool;
    }

    protected void doSend(MimeMessage[] mimeMessages, @Nullable Object[] originalMessages) throws MailException {
        Map<Object, Exception> failedMessages = new LinkedHashMap<>();
        Transport transport = null;

        try {
            transport = transportPool.borrowObject();
            for (int i = 0; i < mimeMessages.length; i++) {
                // Send message via current transport...
                MimeMessage mimeMessage = mimeMessages[i];
                try {
                    if (mimeMessage.getSentDate() == null) {
                        mimeMessage.setSentDate(new Date());
                    }
                    String messageId = mimeMessage.getMessageID();
                    mimeMessage.saveChanges();
                    if (messageId != null) {
                        // Preserve explicitly specified message id...
                        mimeMessage.setHeader(HEADER_MESSAGE_ID, messageId);
                    }
                    Address[] addresses = mimeMessage.getAllRecipients();
                    LOGGER.trace("Sending " + mimeMessage.getMessageID());
                    transport.sendMessage(mimeMessage, (addresses != null ? addresses : new Address[0]));
                } catch (Exception ex) {
                    Object original = (originalMessages != null ? originalMessages[i] : mimeMessage);
                    failedMessages.put(original, ex);
                }
            }
        } catch (Exception ex) {
            throw new MailSendException("Failed to get transport from pool", ex);
        } finally {
            try {
                if (transport != null) {
                    transportPool.returnObject(transport);
                }
            } catch (Exception ex) {
                if (!failedMessages.isEmpty()) {
                    throw new MailSendException("Failed to close server connection after message failures", ex,
                            failedMessages);
                } else {
                    throw new MailSendException("Failed to close server connection after message sending", ex);
                }
            }
        }

        if (!failedMessages.isEmpty()) {
            throw new MailSendException(failedMessages);
        }
    }
}

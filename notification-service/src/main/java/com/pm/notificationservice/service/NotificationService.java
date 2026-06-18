package com.pm.notificationservice.service;

import com.pm.notificationservice.entity.EventType;
import com.pm.notificationservice.entity.Notification;
import com.pm.notificationservice.entity.NotificationStatus;
import com.pm.notificationservice.events.AccountFreezeEvent;
import com.pm.notificationservice.events.AccountUnfreezeEvent;
import com.pm.notificationservice.events.TransferCompletedEvent;
import com.pm.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void handleAccountFrozen(AccountFreezeEvent event) {
        NotificationStatus status;
        try {
            //inject email via event that is consuming, but for now its static
            String subject = "Important: Your Account Has Been Frozen";
            String body = """
            Dear Customer,
            
            We would like to inform you that your account %s has been temporarily frozen.
            
            If this action was unexpected or you require assistance, please contact our support team.
            
            Thank you,
            Banking Application Team
            """.formatted(event.getAccountNumber());
            sendEmailNotification("test1231312313@gmail.com", body, subject);
            status = NotificationStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            status = NotificationStatus.FAILED;
        }

        Notification notification = Notification.builder()
                .userId(event.getUserId().toString())
                .type(EventType.FROZEN)
                .message("Your account " + event.getAccountNumber() + " has been frozen.")
                .sentAt(LocalDateTime.now())
                .status(status)
                .build();

        notificationRepository.save(notification);
    }

    public void handleAccountUnfrozen(AccountUnfreezeEvent event) {
        NotificationStatus status;
        try {
            String subject = "Your Account Has Been Reactivated";

            String body = """
            Dear Customer,
            
            Good news! Your account %s has been successfully reactivated and is now available for use.
            
            Thank you for choosing our services.
            
            Best regards,
            Banking Application Team
            """.formatted(event.getAccountNumber());
            sendEmailNotification("test1231312313@gmail.com", body, subject);
            status = NotificationStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            status = NotificationStatus.FAILED;
        }

        Notification notification = Notification.builder()
                .userId(event.getUserId().toString())
                .type(EventType.ACTIVE)
                .message("Your account " + event.getAccountNumber() + " has been unfrozen.")
                .sentAt(LocalDateTime.now())
                .status(status)
                .build();

        notificationRepository.save(notification);
    }

    public void handleTransferComplete(TransferCompletedEvent event) {
        NotificationStatus status;

        try {
            String subject = "Transfer Completed Successfully";
            String body = """
            Dear Customer,
            
            Your transfer has been completed successfully.
            
            Amount: %s %s
            Transaction ID: %s
            
            Thank you for using our banking services.
            
            Best regards,
            Banking Application Team
            """.formatted(
                                event.getAmount(),
                                event.getCurrency(),
                                event.getTransferId()
                        );
            sendEmailNotification("test1231312313@gmail.com", body, subject);
            status = NotificationStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            status = NotificationStatus.FAILED;
        }

        Notification notification = Notification.builder()
                .userId(event.getInitiatedBy().toString())
                .type(EventType.TRANSFER)
                .message("You sent " + event.getAmount() + " " + event.getCurrency() + " successfully.")
                .sentAt(LocalDateTime.now())
                .status(status)
                .build();

        notificationRepository.save(notification);
    }

    private void sendEmailNotification(String receiver, String body, String subject){
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromEmail);
        message.setTo(receiver);
        message.setText(body);
        message.setSubject(subject);

        mailSender.send(message);
    }
}

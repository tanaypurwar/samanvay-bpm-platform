package com.eternalhr.onboarding.notify;

import com.eternalhr.onboarding.config.EternalHrProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Sends HTML emails rendered from {@code templates/mail/<template>.html}. */
@Component
public class Mailer {

  private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);

  private final JavaMailSender sender;
  private final TemplateEngine templates;
  private final EternalHrProperties properties;

  public Mailer(JavaMailSender sender, TemplateEngine templates, EternalHrProperties properties) {
    this.sender = sender;
    this.templates = templates;
    this.properties = properties;
  }

  public record Email(Collection<String> to, Collection<String> cc, String subject, String template,
      Map<String, Object> model) {

    public static Email to(String to, String subject, String template, Map<String, Object> model) {
      return new Email(to == null ? List.of() : List.of(to), List.of(), subject, template, model);
    }
  }

  /**
   * Sends the email. Recipients without an address are skipped; if nobody is left, nothing is sent and false is
   * returned, so a missing address never blocks the process.
   */
  public boolean send(Email email) {
    List<String> to = clean(email.to());
    if (to.isEmpty()) {
      LOG.warn("Email '{}' not sent: no recipient address", email.subject());
      return false;
    }
    Context context = new Context();
    context.setVariables(email.model());
    context.setVariable("tasklistUrl", properties.tasklistUrl());
    String html = templates.process("mail/" + email.template(), context);
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(properties.mail().from());
      helper.setTo(to.toArray(String[]::new));
      List<String> cc = clean(email.cc());
      if (!cc.isEmpty()) {
        helper.setCc(cc.toArray(String[]::new));
      }
      if (properties.mail().hrEmail() != null && !properties.mail().hrEmail().isBlank()) {
        helper.setReplyTo(properties.mail().hrEmail());
      }
      helper.setSubject(email.subject());
      helper.setText(html, true);
      sender.send(message);
      LOG.info("Sent '{}' to {}", email.subject(), to);
      return true;
    } catch (MessagingException e) {
      throw new IllegalStateException("Could not build email '" + email.subject() + "'", e);
    }
  }

  private static List<String> clean(Collection<String> addresses) {
    return addresses == null ? List.of()
        : addresses.stream().filter(Objects::nonNull).map(String::trim).filter(a -> !a.isEmpty()).distinct().toList();
  }
}

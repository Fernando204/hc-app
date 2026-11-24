package com.horsecontrol.eloquestre.services.auth;

import com.horsecontrol.eloquestre.services.internal.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envio de e-mail por SMTP (spring.mail.* — ver application.properties).
 * Sem SMTP configurado (desenvolvimento), só escreve o e-mail no console do servidor.
 */
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    /** spring.mail.host vazio também cria o JavaMailSender: tratamos como "sem SMTP". */
    private final boolean hostConfigured;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${app.mail.from:ELOequestre <nao-responda@horsecontrol.com.br>}") String from,
                       @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.from = from;
        this.hostConfigured = host != null && !host.isBlank();
    }

    public boolean configured() {
        return hostConfigured && sender.getIfAvailable() != null;
    }

    /** Envia em segundo plano (a resposta da API não espera o SMTP). */
    public void send(String to, String subject, String text) {
        JavaMailSender mail = configured() ? sender.getIfAvailable() : null;
        if (mail == null) {
            Logger.info("[E-MAIL NÃO ENVIADO — configure SMTP] Para: " + to + " | " + subject + "\n" + text);
            return;
        }
        executor.submit(() -> {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(from);
                m.setTo(to);
                m.setSubject(subject);
                m.setText(text);
                mail.send(m);
            } catch (Exception e) {
                Logger.error("Falha ao enviar e-mail para " + to + ": " + e.getMessage());
            }
        });
    }
}

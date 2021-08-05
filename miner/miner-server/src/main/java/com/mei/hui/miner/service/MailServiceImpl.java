package com.mei.hui.miner.service;

import com.mei.hui.miner.model.MailDO;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.SystemException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;

@Service
@Slf4j
public class MailServiceImpl implements MailService{

    //template模板引擎
    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 纯文本邮件
     * @param mail
     */
    @Override
    public void sendTextMail(MailDO mail){
        //建立邮件消息
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); // 发送人的邮箱
        message.setSubject(mail.getTitle()); //标题
        message.setTo(mail.getEmail()); //发给谁  对方邮箱
        message.setText(mail.getContent()); //内容
        try {
            javaMailSender.send(message); //发送
        } catch (MailException e) {
            log.error("纯文本邮件发送失败->message:{}",e.getMessage());
        }
    }

    /**
     * 发送的邮件是富文本（附件，图片，html等）
     * @param mailDO
     * @param isShowHtml 是否解析html
     */
    @Override
    public void sendHtmlMail(MailDO mailDO, boolean isShowHtml) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            //是否发送的邮件是富文本（附件，图片，html等）
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage,true);
            messageHelper.setFrom(from);
            messageHelper.setTo(mailDO.getEmail());
            messageHelper.setSubject(mailDO.getTitle());
            messageHelper.setText(mailDO.getContent(),isShowHtml);//false，显示原始html代码，无效果
            //判断是否有附加图片等
            if(mailDO.getAttachment() != null && mailDO.getAttachment().size() > 0) {
                mailDO.getAttachment().entrySet().stream().forEach(entrySet -> {
                    try {
                        File file = new File(String.valueOf(entrySet.getValue()));
                        if (file.exists()) {
                            messageHelper.addAttachment(entrySet.getKey(), new FileSystemResource(file));
                        }
                    } catch (MessagingException e) {
                        log.error("附件发送失败->message:{}", e.getMessage());
                    }
                });
            }
            //发送
            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("富文本邮件发送失败->message:{}",e.getMessage());
        }
    }

    /**
     * 发送模板邮件 使用thymeleaf模板
     * 若果使用freemarker模板
     *     Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
     *     configuration.setClassForTemplateLoading(this.getClass(), "/templates");
     *     String emailContent = FreeMarkerTemplateUtils.processTemplateIntoString(configuration.getTemplate("mail.ftl"), params);
     * @param mailDO
     */
    @Override
    public void sendTemplateMail(MailDO mailDO) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage,true);
            messageHelper.setFrom(from);// 发送人的邮箱
            messageHelper.setTo(mailDO.getEmail());//发给谁  对方邮箱
            messageHelper.setSubject(mailDO.getTitle()); //标题
            //使用模板thymeleaf
            //Context是导这个包import org.thymeleaf.context.Context;
            Context context = new Context();
            //定义模板数据
            context.setVariables(mailDO.getAttachment());
            //获取thymeleaf的html模板
            String emailContent = templateEngine.process("/mail/mail",context); //指定模板路径
            messageHelper.setText(emailContent,true);
            //发送邮件
            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("模板邮件发送失败->message:{}",e.getMessage());
        }
    }


}
package com.lh.assist.common.mail;

public final class EmailTemplateBuilder {

    private static final String PRIMARY_COLOR = "#9BC24D";

    private EmailTemplateBuilder() {
    }

    public static String buildVerificationEmail(String authCode) {
        StringBuilder emailBody = new StringBuilder();
        emailBody.append("<!DOCTYPE html>");
        emailBody.append("<html lang='ko'>");
        emailBody.append("<head>");
        emailBody.append("<meta charset='UTF-8'>");
        emailBody.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        emailBody.append("<title>LH Assist 이메일 인증</title>");
        emailBody.append("</head>");
        emailBody.append("<body style='font-family: Arial, sans-serif; background-color: #ffffff; margin: 0; padding: 0; text-align: center; color: #111;'>");
        emailBody.append("<div style='max-width: 600px; margin: 40px auto; background: #ffffff; padding: 40px; border-radius: 12px; border: 1px solid #e5e5e5; text-align: center;'>");
        emailBody.append("<div style='font-size: 22px; font-weight: bold; color: #111; margin-bottom: 8px;'>LH Assist 이메일 인증</div>");
        emailBody.append("<div style='font-size: 14px; color: #444;'>이메일 인증을 위한 인증 코드를 발급해드립니다.</div>");
        emailBody.append("<div style='margin: 24px 0;'>");
        emailBody.append("<span style='display: inline-block; padding: 12px 20px; border-radius: 10px; background: #f5f9ec; border: 1px solid #d7e9b8; font-size: 24px; font-weight: bold; letter-spacing: 4px; color: ")
                .append(PRIMARY_COLOR)
                .append(";'>")
                .append(authCode)
                .append("</span>");
        emailBody.append("</div>");
        emailBody.append("<div style='font-size: 14px; color: #444;'>위 코드를 5분 이내에 입력해주세요.</div>");
        emailBody.append("<div style='margin-top: 28px;'>");
        emailBody.append("<a href='https://lh-assist.cloud' style='display: inline-block; padding: 12px 18px; background: ")
                .append(PRIMARY_COLOR)
                .append("; color: #111; text-decoration: none; font-size: 14px; font-weight: bold; border-radius: 8px;'>LH Assist 바로가기</a>");
        emailBody.append("</div>");
        emailBody.append("<p style='margin-top: 28px; font-size: 12px; color: #666;'>이 메일은 자동 발송된 메일입니다.</p>");
        emailBody.append("</div>");
        emailBody.append("</body>");
        emailBody.append("</html>");
        return emailBody.toString();
    }

    public static String buildTempPasswordEmail(String tempPassword) {
        StringBuilder emailBody = new StringBuilder();
        emailBody.append("<!DOCTYPE html>");
        emailBody.append("<html lang='ko'>");
        emailBody.append("<head>");
        emailBody.append("<meta charset='UTF-8'>");
        emailBody.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        emailBody.append("<title>LH Assist 임시 비밀번호</title>");
        emailBody.append("</head>");
        emailBody.append("<body style='font-family: Arial, sans-serif; background-color: #ffffff; margin: 0; padding: 0; text-align: center; color: #111;'>");
        emailBody.append("<div style='max-width: 600px; margin: 40px auto; background: #ffffff; padding: 40px; border-radius: 12px; border: 1px solid #e5e5e5; text-align: center;'>");
        emailBody.append("<div style='font-size: 22px; font-weight: bold; color: #111; margin-bottom: 8px;'>임시 비밀번호 안내</div>");
        emailBody.append("<div style='font-size: 14px; color: #444;'>요청하신 임시 비밀번호를 안내드립니다.</div>");
        emailBody.append("<div style='margin: 24px 0;'>");
        emailBody.append("<div style='font-size: 14px; color: #444; margin-bottom: 8px;'>임시 비밀번호:</div>");
        emailBody.append("<span style='display: inline-block; padding: 12px 20px; border-radius: 10px; background: #f5f9ec; border: 1px solid #d7e9b8; font-size: 22px; font-weight: bold; letter-spacing: 2px; color: ")
                .append(PRIMARY_COLOR)
                .append(";'>")
                .append(tempPassword)
                .append("</span>");
        emailBody.append("</div>");
        emailBody.append("<div style='font-size: 14px; color: #444;'>로그인 후 반드시 비밀번호를 변경해주세요.</div>");
        emailBody.append("<div style='margin-top: 28px;'>");
        emailBody.append("<a href='https://lh-assist.cloud' style='display: inline-block; padding: 12px 18px; background: ")
                .append(PRIMARY_COLOR)
                .append("; color: #111; text-decoration: none; font-size: 14px; font-weight: bold; border-radius: 8px;'>LH Assist 바로가기</a>");
        emailBody.append("</div>");
        emailBody.append("<p style='margin-top: 28px; font-size: 12px; color: #666;'>이 메일은 자동 발송된 메일입니다.</p>");
        emailBody.append("</div>");
        emailBody.append("</body>");
        emailBody.append("</html>");
        return emailBody.toString();
    }
}
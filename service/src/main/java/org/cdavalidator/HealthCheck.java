package org.cdavalidator;
import java.net.*;
public final class HealthCheck {
    public static void main(String[] args) {
        try {
            var connection=(HttpURLConnection)URI.create("http://127.0.0.1:"+System.getenv().getOrDefault("PORT","17643")+"/health/ready").toURL().openConnection();
            connection.setConnectTimeout(2000);connection.setReadTimeout(2000);
            int code=connection.getResponseCode();connection.disconnect();System.exit(code==200?0:1);
        } catch(Exception e) { System.exit(1); }
    }
}

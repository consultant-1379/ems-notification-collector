/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/
package com.ericsson.oss.adc.emsnc.client.enm;

import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class RetrofitConfiguration {

  boolean trustAllCertificates = false;

  /**
   * Creates Retrofit Builder with baseUrl provided.
   *
   * @param location baseUrl for Retrofit Builder
   * @return Retrofit Builder with baseUrl provided
   */
  public Retrofit createEnmBuilder(URI location) {

    return new Retrofit.Builder()
        .baseUrl(HttpUrl.get(location))
        .addConverterFactory(GsonConverterFactory.create())
        .client(createEnmClient())
        .build();
  }

  /**
   * Method creates OkHttpClient for requests to enm. It is not passing JWT, because ENM is not able
   * to retrieve and process it.
   *
   * @return OkHttpClient
   */
  private OkHttpClient createEnmClient() {
    OkHttpClient.Builder builder = new OkHttpClient().newBuilder();

    addLogging(builder);
    configureSsl(builder);

    return builder
        .followRedirects(false)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build();
  }

  private void addLogging(OkHttpClient.Builder client) {
    Logger logger = LoggerFactory.getLogger(HttpLoggingInterceptor.class);
    Interceptor customInterceptor =
        chain -> {
          StringBuilder logMessage = new StringBuilder();

          HttpLoggingInterceptor interceptor =
              new HttpLoggingInterceptor(message -> logMessage.append(message).append(' '));
          interceptor.setLevel(Level.BODY);
          interceptor.redactHeader("Authorization");
          interceptor.redactHeader("Cookie");

          Response response = interceptor.intercept(chain);

          StringBuilder logMessageMasked = new StringBuilder();
          if (logMessage.toString().contains("login")) {
            Pattern tokenPattern = Pattern.compile("\\bIDToken2=.*?\\s\\b");
            Matcher tokenPatternMatcher = tokenPattern.matcher(logMessage);
            String passwordMaskedString = tokenPatternMatcher.replaceAll("IDToken2=******* ");
            if (passwordMaskedString.contains("Set-Cookie")) {
              tokenPattern = Pattern.compile("\\bSet-Cookie:\\s.*?\\s\\b");
              tokenPatternMatcher = tokenPattern.matcher(passwordMaskedString);
              logMessageMasked.append(tokenPatternMatcher.replaceAll(""));
            } else {
              logMessageMasked.append(passwordMaskedString);
            }
          }

          if (logMessageMasked.length() > 0) {
            logger.debug("{}", logMessageMasked);
          }

          return response;
        };

    client.addNetworkInterceptor(customInterceptor);
  }

  private void configureSsl(OkHttpClient.Builder builder) {

    if (trustAllCertificates) {
      log.warn("configureSsl with trustAllCertificates = ", trustAllCertificates);
      try {
        trustAllCertificates(builder);
      } catch (Exception e) {
        throw new SslClientConfigurationException(e);
      }
    }
  }

  private void trustAllCertificates(OkHttpClient.Builder builder)
      throws KeyManagementException, NoSuchAlgorithmException {
    // Create a trust manager that does not validate certificate chains

    final X509TrustManager manager =
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(
              java.security.cert.X509Certificate[] chain, String authType) {
            log.debug("Creating a trust manager that does not validate certificate chains");
          }

          @Override
          public void checkServerTrusted(
              java.security.cert.X509Certificate[] chain, String authType) {
            log.debug("Creating a trust manager that does not validate certificate chains");
          }

          @Override
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[] {};
          }
        };

    final TrustManager[] trustAllCerts = new TrustManager[] {manager};

    // Install the all-trusting trust manager
    final SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    // Create an ssl socket factory with our all-trusting manager
    final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

    builder.sslSocketFactory(sslSocketFactory, manager);
    builder.hostnameVerifier((hostname, session) -> true);
  }
}

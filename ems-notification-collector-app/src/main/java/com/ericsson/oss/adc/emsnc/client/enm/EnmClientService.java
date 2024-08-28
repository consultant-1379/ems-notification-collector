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

import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmCommandResponseData;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmNodeData;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.stereotype.Service;
import retrofit2.Response;
import retrofit2.Retrofit;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnmClientService {

  private static final String TEXT_PLAIN = "text/plain";
  private final RetrofitConfiguration configuration;

  // TODO items never purged from the cache
  private final Map<URI, EnmClient> enmClientCache = new ConcurrentHashMap<>();
  private final ReadWriteLock clientCacheLock = new ReentrantReadWriteLock();
  private final Map<EmsCredentials, String> tokenCache = new ConcurrentHashMap<>();
  private final ReadWriteLock tokenCacheLock = new ReentrantReadWriteLock();
  private final Lock tokenUpdateLock = new ReentrantLock();

  public LoginHandlingEnmClient getEnmClient(EmsCredentials emsCredentials) {
    Lock readLock = clientCacheLock.readLock();
    Lock writeLock = clientCacheLock.writeLock();
    try {
      readLock.lock();
      if (!enmClientCache.containsKey(emsCredentials.getLocation())) {
        try {
          readLock.unlock();
          writeLock.lock();
          if (!enmClientCache.containsKey(emsCredentials.getLocation())) {
            // threads may queue up for the write lock, need to check again
            log.info("Initializing ENM client for {}", emsCredentials.getLocation());
            Retrofit enmBuilder = configuration.createEnmBuilder(emsCredentials.getLocation());
            EnmClient enmClient = enmBuilder.create(EnmClient.class);
            enmClientCache.put(emsCredentials.getLocation(), enmClient);
          }
        } finally {
          writeLock.unlock();
          readLock.lock();
        }
      }
      return new TokenCachingEnmClient(
          enmClientCache.get(emsCredentials.getLocation()), emsCredentials);
    } finally {
      readLock.unlock();
    }
  }

  @RequiredArgsConstructor
  private class TokenCachingEnmClient implements LoginHandlingEnmClient {

    private final EnmClient delegate;
    private final EmsCredentials emsCredentials;

    @Override
    public void logout() {
      try {
        delegate.logout(getCachedToken()).execute();
      } catch (IOException e) {
        // no recovery needed, just remove the invalid token
        log.debug("Could not send logout request, ignoring", e);
      }
      tokenCache.remove(emsCredentials);
    }

    @Override
    public EnmEventsResponse getVnfConfigEvents(
        String eventDetectionTimestamp, String filterClauses) {
      return executeCall(
          token ->
              delegate.getConfigEvents(token, eventDetectionTimestamp, filterClauses).execute());
    }

    @Override
    public List<EnmNodeData> getNetworkElements(String neType) {
      String requestId =
          sendEnmCmCommand(
              RequestBody.create(MediaType.parse(TEXT_PLAIN), "command"),
              RequestBody.create(MediaType.parse(TEXT_PLAIN), "true"),
              RequestBody.create(
                  MediaType.parse(TEXT_PLAIN), "cmedit get * NetworkElement --netype=" + neType));

      EnmCommandResponseData result = getEnmCmCommandOutput(requestId);

      return result.getOutput().getElements();
    }

    private String sendEnmCmCommand(
        RequestBody name, RequestBody streamOutput, RequestBody command) {
      try {
        return executeCall(
                token -> delegate.sendEnmCmCommand(token, name, streamOutput, command).execute())
            .string();
      } catch (IOException e) {
        log.error("Parsing sendEnmCmCommand response failed, reason: ", e);
        throw new InvalidResponseException(e);
      }
    }

    private EnmCommandResponseData getEnmCmCommandOutput(String jobId) {
      return executeCall(token -> delegate.getEnmCmCommandOutput(token, jobId).execute());
    }

    private <T> T executeCall(RestCall<T> supplier) {
      try {
        String firstTryToken = getCachedToken();
        Response<T> response = supplier.call(firstTryToken);
        if (response.isSuccessful()) {
          return response.body();
        } else {
          // invalidate the expired token with an extra lock to avoid all threads invalidating it
          try {
            tokenUpdateLock.lock();
            if (getCachedToken().equals(firstTryToken)) {
              try {
                tokenCacheLock.writeLock().lock();
                tokenCache.remove(emsCredentials);
              } finally {
                tokenCacheLock.writeLock().unlock();
              }
            }
          } finally {
            tokenUpdateLock.unlock();
          }
        }
        response = supplier.call(getCachedToken());
        if (response.isSuccessful()) {
          return response.body();
        } else {
          throw new MissingAuthenticationTokenException();
        }

      } catch (IOException e) {
        throw new NetworkException(e);
      }
    }

    private String getCachedToken() {
      Lock readLock = tokenCacheLock.readLock();
      Lock writeLock = tokenCacheLock.writeLock();
      try {
        readLock.lock();
        if (!tokenCache.containsKey(emsCredentials)) {
          try {
            readLock.unlock();
            writeLock.lock();
            if (!tokenCache.containsKey(emsCredentials)) {
              log.info("Updating cached token for {}", emsCredentials.getLocation());
              String token = EnmLogin.queryForAuthenticationToken(emsCredentials, delegate);
              tokenCache.put(emsCredentials, token);
            }
          } finally {
            writeLock.unlock();
            readLock.lock();
          }
        }
        return tokenCache.get(emsCredentials);
      } finally {
        readLock.unlock();
      }
    }
  }

  @FunctionalInterface
  private interface RestCall<T> {
    Response<T> call(String token) throws IOException;
  }
}

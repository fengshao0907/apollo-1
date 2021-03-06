package com.ctrip.framework.apollo.portal;


import com.ctrip.framework.apollo.core.enums.Env;
import com.ctrip.framework.apollo.portal.api.AdminServiceAPI;
import com.ctrip.framework.apollo.portal.service.ServerConfigService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

@Component
public class PortalSettings {

  private Logger logger = LoggerFactory.getLogger(PortalSettings.class);

  private static final int HEALTH_CHECK_INTERVAL = 10 * 1000;


  @Autowired
  ApplicationContext applicationContext;

  @Autowired
  private ServerConfigService serverConfigService;

  private List<Env> allEnvs = new ArrayList<>();

  //mark env up or down
  private Map<Env, Boolean> envStatusMark = new ConcurrentHashMap<>();

  private ScheduledExecutorService healthCheckService;

  @PostConstruct
  private void postConstruct() {

    //初始化portal支持操作的环境集合,线上的portal可能支持所有的环境操作,而线下环境则支持一部分.
    // 每个环境的portal支持哪些环境配置在数据库里
    String serverConfig = serverConfigService.getValue("apollo.portal.envs", "FAT,UAT,PRO");
    String[] configedEnvs = serverConfig.split(",");
    List<String> allStrEnvs = Arrays.asList(configedEnvs);
    for (String e : allStrEnvs) {
      allEnvs.add(Env.valueOf(e.toUpperCase()));
    }

    for (Env env : allEnvs) {
      envStatusMark.put(env, true);
    }

    healthCheckService = Executors.newScheduledThreadPool(1);

    healthCheckService
        .scheduleWithFixedDelay(new HealthCheckTask(applicationContext), 1000, HEALTH_CHECK_INTERVAL,
            TimeUnit.MILLISECONDS);

  }

  public List<Env> getAllEnvs() {
    return allEnvs;
  }

  public List<Env> getActiveEnvs() {
    List<Env> activeEnvs = new LinkedList<>();
    for (Env env : allEnvs) {
      if (envStatusMark.get(env)) {
        activeEnvs.add(env);
      }
    }
    return activeEnvs;
  }

  class HealthCheckTask implements Runnable {

    private static final int ENV_DIED_THREADHOLD = 2;

    private Map<Env, Long> healthCheckFailCnt = new HashMap<>();

    private AdminServiceAPI.HealthAPI healthAPI;

    public HealthCheckTask(ApplicationContext context) {
      healthAPI = context.getBean(AdminServiceAPI.HealthAPI.class);
      for (Env env : allEnvs) {
        healthCheckFailCnt.put(env, 0l);
      }
    }

    public void run() {

      for (Env env : allEnvs) {
        try {
          if (isUp(env)) {
            //revive
            if (!envStatusMark.get(env)) {
              envStatusMark.put(env, true);
              healthCheckFailCnt.put(env, 0l);
              logger.info("env up again [env:{}]", env);
            }
          } else {
            //maybe meta server up but admin server down
            handleEnvDown(env);
          }

        } catch (Exception e) {
          //maybe meta server down
          logger.warn("health check fail. [env:{}]", env, e.getMessage());
          handleEnvDown(env);
        }
      }

    }

    private boolean isUp(Env env) {
      Health health = healthAPI.health(env);
      return "UP".equals(health.getStatus().getCode());
    }

    private void handleEnvDown(Env env) {
      long failCnt = healthCheckFailCnt.get(env);
      healthCheckFailCnt.put(env, ++failCnt);

      if (!envStatusMark.get(env)) {
        logger.warn("[env:{}] down yet.", env);
      } else {
        if (failCnt >= ENV_DIED_THREADHOLD) {
          envStatusMark.put(env, false);
          logger.error("env turn to down [env:{}]", env);
        } else {
          logger.warn("env health check fail first time. [env:{}]", env);
        }
      }

    }

  }
}

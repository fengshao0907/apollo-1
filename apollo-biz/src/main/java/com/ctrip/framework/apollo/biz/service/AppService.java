package com.ctrip.framework.apollo.biz.service;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ctrip.framework.apollo.common.entity.App;
import com.ctrip.framework.apollo.biz.entity.Audit;
import com.ctrip.framework.apollo.biz.repository.AppRepository;
import com.ctrip.framework.apollo.common.utils.BeanUtils;
import com.ctrip.framework.apollo.common.exception.ServiceException;

@Service
public class AppService {

  @Autowired
  private AppRepository appRepository;

  @Autowired
  private AuditService auditService;

  public boolean isAppIdUnique(String appId) {
    Objects.requireNonNull(appId, "AppId must not be null");
    return Objects.isNull(appRepository.findByAppId(appId));
  }
  
  @Transactional
  public void delete(long id, String operator) {
    App app = appRepository.findOne(id);
    if (app == null) {
      return;
    }

    app.setDeleted(true);
    app.setDataChangeLastModifiedBy(operator);
    appRepository.save(app);

    auditService.audit(App.class.getSimpleName(), id, Audit.OP.DELETE, operator);
  }

  public List<App> findAll(Pageable pageable) {
    Page<App> page = appRepository.findAll(pageable);
    return page.getContent();
  }

  public List<App> findByName(String name) {
    return appRepository.findByName(name);
  }

  public App findOne(String appId) {
    return appRepository.findByAppId(appId);
  }

  @Transactional
  public App save(App entity) {
    if (!isAppIdUnique(entity.getAppId())) {
      throw new ServiceException("appId not unique");
    }
    entity.setId(0);//protection
    App app = appRepository.save(entity);
    
    auditService.audit(App.class.getSimpleName(), app.getId(), Audit.OP.INSERT,
        app.getDataChangeCreatedBy());
    
    return app;
  }

  @Transactional
  public App update(App app) {
    App managedApp = appRepository.findByAppId(app.getAppId());
    BeanUtils.copyEntityProperties(app, managedApp);
    managedApp = appRepository.save(managedApp);
    
    auditService.audit(App.class.getSimpleName(), managedApp.getId(), Audit.OP.UPDATE,
        managedApp.getDataChangeLastModifiedBy());
    
    return managedApp;
  }
}

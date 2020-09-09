/*
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.controller;

import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.ACTIVE_STATUS;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.INACTIVE_STATUS;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.USER_ID_HEADER;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.YES;
import static com.google.cloud.healthcare.fdamystudies.common.ErrorCode.ALREADY_DECOMMISSIONED;
import static com.google.cloud.healthcare.fdamystudies.common.ErrorCode.CANNOT_REACTIVATE;
import static com.google.cloud.healthcare.fdamystudies.common.ErrorCode.DEFAULT_SITE_MODIFY_DENIED;
import static com.google.cloud.healthcare.fdamystudies.common.ErrorCode.LOCATION_ACCESS_DENIED;
import static com.google.cloud.healthcare.fdamystudies.common.ErrorCode.LOCATION_NOT_FOUND;
import static com.google.cloud.healthcare.fdamystudies.common.JsonUtils.asJsonString;
import static com.google.cloud.healthcare.fdamystudies.common.JsonUtils.readJsonFile;
import static com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent.LOCATION_ACTIVATED;
import static com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent.LOCATION_EDITED;
import static com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent.NEW_LOCATION_ADDED;
import static com.google.cloud.healthcare.fdamystudies.common.TestConstants.CUSTOM_ID_VALUE;
import static com.google.cloud.healthcare.fdamystudies.common.TestConstants.LOCATION_DESCRIPTION_VALUE;
import static com.google.cloud.healthcare.fdamystudies.common.TestConstants.LOCATION_NAME_VALUE;
import static com.google.cloud.healthcare.fdamystudies.common.TestConstants.UPDATE_LOCATION_DESCRIPTION_VALUE;
import static com.google.cloud.healthcare.fdamystudies.common.TestConstants.UPDATE_LOCATION_NAME_VALUE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.cloud.healthcare.fdamystudies.beans.AuditLogEventRequest;
import com.google.cloud.healthcare.fdamystudies.beans.LocationRequest;
import com.google.cloud.healthcare.fdamystudies.beans.UpdateLocationRequest;
import com.google.cloud.healthcare.fdamystudies.common.ApiEndpoint;
import com.google.cloud.healthcare.fdamystudies.common.BaseMockIT;
import com.google.cloud.healthcare.fdamystudies.common.CommonConstants;
import com.google.cloud.healthcare.fdamystudies.common.IdGenerator;
import com.google.cloud.healthcare.fdamystudies.common.MessageCode;
import com.google.cloud.healthcare.fdamystudies.common.ParticipantManagerEvent;
import com.google.cloud.healthcare.fdamystudies.common.Permission;
import com.google.cloud.healthcare.fdamystudies.helper.TestDataHelper;
import com.google.cloud.healthcare.fdamystudies.model.AppEntity;
import com.google.cloud.healthcare.fdamystudies.model.LocationEntity;
import com.google.cloud.healthcare.fdamystudies.model.SiteEntity;
import com.google.cloud.healthcare.fdamystudies.model.StudyEntity;
import com.google.cloud.healthcare.fdamystudies.model.UserRegAdminEntity;
import com.google.cloud.healthcare.fdamystudies.repository.LocationRepository;
import com.google.cloud.healthcare.fdamystudies.repository.UserRegAdminRepository;
import com.google.cloud.healthcare.fdamystudies.service.LocationService;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

public class LocationControllerTest extends BaseMockIT {

  @Autowired private LocationController controller;

  @Autowired private LocationService locationService;

  @Autowired private TestDataHelper testDataHelper;

  @Autowired private LocationRepository locationRepository;

  @Autowired private UserRegAdminRepository userRegAdminRepository;

  private UserRegAdminEntity userRegAdminEntity;

  private LocationEntity locationEntity;

  protected MvcResult result;

  private AppEntity appEntity;

  private StudyEntity studyEntity;

  private SiteEntity siteEntity;

  @BeforeEach
  public void setUp() {

    userRegAdminEntity = testDataHelper.createUserRegAdmin();
    locationEntity = testDataHelper.createLocation();
    appEntity = testDataHelper.createAppEntity(userRegAdminEntity);
    studyEntity = testDataHelper.createStudyEntity(userRegAdminEntity, appEntity);
    siteEntity = testDataHelper.createSiteEntity(studyEntity, userRegAdminEntity, appEntity);
  }

  @Test
  public void contextLoads() {
    assertNotNull(controller);
    assertNotNull(mockMvc);
    assertNotNull(locationService);
  }

  @Test
  public void shouldReturnBadRequestForAddNewLocation() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    LocationRequest locationRequest = new LocationRequest();
    result =
        mockMvc
            .perform(
                post(ApiEndpoint.ADD_NEW_LOCATION.getPath())
                    .content(asJsonString(locationRequest))
                    .headers(headers)
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations").isArray())
            .andReturn();

    String actualResponse = result.getResponse().getContentAsString();
    String expectedResponse = readJsonFile("/responses/add_location_bad_request.json");
    JSONAssert.assertEquals(expectedResponse, actualResponse, JSONCompareMode.NON_EXTENSIBLE);

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnForbiddenForLocationAccessDenied() throws Exception {

    userRegAdminEntity.setLocationPermission(Permission.READ_VIEW.value());
    userRegAdminRepository.saveAndFlush(userRegAdminEntity);

    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            post(ApiEndpoint.ADD_NEW_LOCATION.getPath())
                .content(asJsonString(getLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error_description", is(LOCATION_ACCESS_DENIED.getDescription())))
        .andReturn();

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldCreateANewLocation() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    // Step 1: Call API to create new location
    result =
        mockMvc
            .perform(
                post(ApiEndpoint.ADD_NEW_LOCATION.getPath())
                    .content(asJsonString(getLocationRequest()))
                    .headers(headers)
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.locationId", notNullValue()))
            .andExpect(jsonPath("$.message", is(MessageCode.ADD_LOCATION_SUCCESS.getMessage())))
            .andReturn();

    String locationId = JsonPath.read(result.getResponse().getContentAsString(), "$.locationId");

    // Step 2: verify saved values
    Optional<LocationEntity> optLocationEntity = locationRepository.findById(locationId);
    LocationEntity locationEntity = optLocationEntity.get();
    assertNotNull(locationEntity);
    assertEquals(CUSTOM_ID_VALUE, locationEntity.getCustomId());
    assertEquals(LOCATION_NAME_VALUE, locationEntity.getName());
    assertEquals(LOCATION_DESCRIPTION_VALUE, locationEntity.getDescription());

    AuditLogEventRequest auditRequest = new AuditLogEventRequest();
    auditRequest.setUserId(userRegAdminEntity.getId());
    Map<String, AuditLogEventRequest> auditEventMap = new HashedMap<>();
    auditEventMap.put(NEW_LOCATION_ADDED.getEventCode(), auditRequest);

    verifyAuditEventCall(auditEventMap, NEW_LOCATION_ADDED);

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnBadRequestForDefaultSiteModify() throws Exception {
    // Step 1: change default value to yes
    locationEntity.setIsDefault(YES);
    locationRepository.saveAndFlush(locationEntity);

    // Step 2: call the API and assert the error description
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            put(ApiEndpoint.UPDATE_LOCATION.getPath(), locationEntity.getId())
                .content(asJsonString(getUpdateLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.error_description", is(DEFAULT_SITE_MODIFY_DENIED.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnBadRequestForCannotReactivate() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    UpdateLocationRequest updateLocationRequest = new UpdateLocationRequest();
    updateLocationRequest.setStatus(ACTIVE_STATUS);

    mockMvc
        .perform(
            put(ApiEndpoint.UPDATE_LOCATION.getPath(), locationEntity.getId())
                .content(asJsonString(updateLocationRequest))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error_description", is(CANNOT_REACTIVATE.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnBadRequestForCannotDecommissioned() throws Exception {
    // Step 1: change the status to inactive
    locationEntity.setStatus(INACTIVE_STATUS);
    locationRepository.saveAndFlush(locationEntity);

    // Step 2: call the API and expect ALREADY_DECOMMISSIONED error
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    UpdateLocationRequest updateLocationRequest = new UpdateLocationRequest();
    updateLocationRequest.setStatus(INACTIVE_STATUS);
    mockMvc
        .perform(
            put(ApiEndpoint.UPDATE_LOCATION.getPath(), locationEntity.getId())
                .content(asJsonString(updateLocationRequest))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error_description", is(ALREADY_DECOMMISSIONED.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnLocationNotFound() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            put(ApiEndpoint.UPDATE_LOCATION.getPath(), IdGenerator.id())
                .content(asJsonString(getUpdateLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error_description", is(LOCATION_NOT_FOUND.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldUpdateALocation() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    // Step 1: Call API to update location
    result =
        mockMvc
            .perform(
                put(ApiEndpoint.UPDATE_LOCATION.getPath(), locationEntity.getId())
                    .content(asJsonString(getUpdateLocationRequest()))
                    .headers(headers)
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationId", notNullValue()))
            .andExpect(jsonPath("$.message", is(MessageCode.LOCATION_UPDATE_SUCCESS.getMessage())))
            .andReturn();

    String locationId = JsonPath.read(result.getResponse().getContentAsString(), "$.locationId");

    // Step 2: verify updated values
    Optional<LocationEntity> optLocationEntity = locationRepository.findById(locationId);
    LocationEntity locationEntity = optLocationEntity.get();
    assertNotNull(locationEntity);
    assertEquals(UPDATE_LOCATION_NAME_VALUE, locationEntity.getName());
    assertEquals(UPDATE_LOCATION_DESCRIPTION_VALUE, locationEntity.getDescription());

    AuditLogEventRequest auditRequest = new AuditLogEventRequest();
    auditRequest.setUserId(userRegAdminEntity.getId());

    Map<String, AuditLogEventRequest> auditEventMap = new HashedMap<>();
    auditEventMap.put(LOCATION_EDITED.getEventCode(), auditRequest);

    verifyAuditEventCall(auditEventMap, LOCATION_EDITED);

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldUpdateToReactiveLocation() throws Exception {
    // Step 1: change the status to inactive
    locationEntity.setStatus(INACTIVE_STATUS);
    locationRepository.saveAndFlush(locationEntity);

    // Step 2: Call API and expect REACTIVE_SUCCESS message
    UpdateLocationRequest updateLocationRequest = new UpdateLocationRequest();
    updateLocationRequest.setStatus(ACTIVE_STATUS);
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    result =
        mockMvc
            .perform(
                put(ApiEndpoint.UPDATE_LOCATION.getPath(), locationEntity.getId())
                    .content(asJsonString(updateLocationRequest))
                    .headers(headers)
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationId", notNullValue()))
            .andExpect(jsonPath("$.message", is(MessageCode.REACTIVE_SUCCESS.getMessage())))
            .andReturn();

    String locationId = JsonPath.read(result.getResponse().getContentAsString(), "$.locationId");

    // Step 3: verify updated values
    Optional<LocationEntity> optLocationEntity = locationRepository.findById(locationId);
    LocationEntity locationEntity = optLocationEntity.get();
    assertNotNull(locationEntity);
    assertEquals(ACTIVE_STATUS, locationEntity.getStatus());

    AuditLogEventRequest auditRequest = new AuditLogEventRequest();
    auditRequest.setUserId(userRegAdminEntity.getId());

    Map<String, AuditLogEventRequest> auditEventMap = new HashedMap<>();
    auditEventMap.put(LOCATION_ACTIVATED.getEventCode(), auditRequest);

    verifyAuditEventCall(auditEventMap, LOCATION_ACTIVATED);

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldUpdateToDeactiveLocation() throws Exception {
    // Step 1: change the status to active
    LocationEntity entityToDeactivateLocation = testDataHelper.newLocationEntity();
    locationRepository.saveAndFlush(entityToDeactivateLocation);
    entityToDeactivateLocation.setStatus(ACTIVE_STATUS);
    locationRepository.saveAndFlush(entityToDeactivateLocation);

    // Step 2: Call API and expect DECOMMISSION_SUCCESS message
    UpdateLocationRequest updateLocationRequest = new UpdateLocationRequest();
    updateLocationRequest.setStatus(INACTIVE_STATUS);
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    result =
        mockMvc
            .perform(
                put(ApiEndpoint.UPDATE_LOCATION.getPath(), entityToDeactivateLocation.getId())
                    .content(asJsonString(updateLocationRequest))
                    .headers(headers)
                    .contextPath(getContextPath()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.locationId", notNullValue()))
            .andExpect(jsonPath("$.message", is(MessageCode.DECOMMISSION_SUCCESS.getMessage())))
            .andReturn();

    String locationId = JsonPath.read(result.getResponse().getContentAsString(), "$.locationId");

    // Step 3: verify updated values
    Optional<LocationEntity> optLocationEntity = locationRepository.findById(locationId);
    LocationEntity locationEntity = optLocationEntity.get();
    assertNotNull(locationEntity);
    assertEquals(INACTIVE_STATUS, locationEntity.getStatus());

    AuditLogEventRequest auditRequest = new AuditLogEventRequest();
    auditRequest.setUserId(userRegAdminEntity.getId());

    Map<String, AuditLogEventRequest> auditEventMap = new HashedMap<>();
    auditEventMap.put(ParticipantManagerEvent.LOCATION_DECOMMISSIONED.getEventCode(), auditRequest);

    verifyAuditEventCall(auditEventMap, ParticipantManagerEvent.LOCATION_DECOMMISSIONED);

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnForbiddenForLocationAccessDeniedOfGetLocations() throws Exception {
    // Step 1: change editPermission to null
    userRegAdminEntity.setLocationPermission(Permission.NO_PERMISSION.value());
    userRegAdminRepository.saveAndFlush(userRegAdminEntity);

    // Step 2: Call API and expect error message LOCATION_ACCESS_DENIED
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATIONS.getPath()).headers(headers).contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error_description", is(LOCATION_ACCESS_DENIED.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnNoLocationsForSiteExcludedByStudyId() throws Exception {
    // Step 1: Set studies for location
    siteEntity.setStudy(studyEntity);
    siteEntity.getStudy().setName("LIMITJP001");
    locationEntity.addSiteEntity(siteEntity);
    testDataHelper.getLocationRepository().save(locationEntity);

    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    // Step 2: Call API and expect message GET_LOCATION_FOR_SITE_SUCCESS
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATIONS.getPath())
                .queryParam("excludeStudyId", studyEntity.getId())
                .queryParam("status", String.valueOf(CommonConstants.ACTIVE_STATUS))
                .content(asJsonString(getLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message", is(MessageCode.GET_LOCATION_FOR_SITE_SUCCESS.getMessage())))
        .andExpect(jsonPath("$.locations").isArray())
        .andExpect(jsonPath("$.locations", hasSize(0)));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnLocations() throws Exception {
    // Step 1: Set studies for location
    SiteEntity siteEntity = testDataHelper.newSiteEntity();
    siteEntity.setStudy(testDataHelper.newStudyEntity());
    siteEntity.getStudy().setName("LIMITJP001");
    locationEntity.addSiteEntity(siteEntity);
    testDataHelper.getLocationRepository().save(locationEntity);

    // Step 2: Call API and expect GET_LOCATION_SUCCESS message
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATIONS.getPath()).headers(headers).contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locations").isArray())
        .andExpect(jsonPath("$.locations[0].locationId", notNullValue()))
        .andExpect(jsonPath("$.locations", hasSize(1)))
        .andExpect(jsonPath("$.locations[0].customId", is("OpenStudy02")))
        .andExpect(jsonPath("$.locations[0].studyNames").isArray())
        .andExpect(jsonPath("$.locations[0].studyNames[0]", is("LIMITJP001")))
        .andExpect(jsonPath("$.message", is(MessageCode.GET_LOCATION_SUCCESS.getMessage())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnForbiddenForLocationForSiteAccessDenied() throws Exception {
    // Step 1: change editPermission to null
    userRegAdminEntity.setLocationPermission(Permission.NO_PERMISSION.value());
    userRegAdminRepository.saveAndFlush(userRegAdminEntity);

    // Step 2: Call API and expect error message LOCATION_ACCESS_DENIED
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATIONS.getPath())
                .queryParam("excludeStudyId", studyEntity.getId())
                .queryParam("status", String.valueOf(CommonConstants.ACTIVE_STATUS))
                .content(asJsonString(getLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error_description", is(LOCATION_ACCESS_DENIED.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnLocationsForSite() throws Exception {
    // Step 1: Set studies for location
    siteEntity.setStudy(testDataHelper.newStudyEntity());
    siteEntity.getStudy().setName("LIMITJP001");
    locationEntity.addSiteEntity(siteEntity);
    testDataHelper.getLocationRepository().save(locationEntity);

    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    // Step 2: Call API and expect message GET_LOCATION_FOR_SITE_SUCCESS
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATIONS.getPath())
                .queryParam("excludeStudyId", studyEntity.getId())
                .queryParam("status", String.valueOf(CommonConstants.ACTIVE_STATUS))
                .content(asJsonString(getLocationRequest()))
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message", is(MessageCode.GET_LOCATION_FOR_SITE_SUCCESS.getMessage())))
        .andExpect(jsonPath("$.locations").isArray())
        .andExpect(jsonPath("$.locations", hasSize(1)))
        .andExpect(jsonPath("$.locations[0].locationId", notNullValue()))
        .andExpect(jsonPath("$.locations[0].customId", is("OpenStudy02")));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnNotFoundForGetLocationById() throws Exception {
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATION_BY_LOCATION_ID.getPath(), IdGenerator.id())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error_description", is(LOCATION_NOT_FOUND.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnForbiddenForLocationAccessDeniedById() throws Exception {
    // Step 1: change editPermission to null
    userRegAdminEntity.setLocationPermission(Permission.NO_PERMISSION.value());
    userRegAdminRepository.saveAndFlush(userRegAdminEntity);

    // Step 2: Call API and expect error message LOCATION_ACCESS_DENIED
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATION_BY_LOCATION_ID.getPath(), locationEntity.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error_description", is(LOCATION_ACCESS_DENIED.getDescription())));

    verifyTokenIntrospectRequest();
  }

  @Test
  public void shouldReturnLocationById() throws Exception {
    // Step 1: Set studies for location
    SiteEntity siteEntity = testDataHelper.newSiteEntity();
    siteEntity.setStudy(testDataHelper.newStudyEntity());
    siteEntity.getStudy().setName("LIMITJP001");
    locationEntity.addSiteEntity(siteEntity);
    testDataHelper.getLocationRepository().save(locationEntity);

    // Step 2: Call API and expect GET_LOCATION_SUCCESS message
    HttpHeaders headers = testDataHelper.newCommonHeaders();
    headers.set(USER_ID_HEADER, userRegAdminEntity.getId());
    mockMvc
        .perform(
            get(ApiEndpoint.GET_LOCATION_BY_LOCATION_ID.getPath(), locationEntity.getId())
                .headers(headers)
                .contextPath(getContextPath()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.locationId", is(locationEntity.getId())))
        .andExpect(jsonPath("$.studyNames").isArray())
        .andExpect(jsonPath("$.studyNames", hasSize(1)))
        .andExpect(jsonPath("$.studyNames[0]", is("LIMITJP001")))
        .andExpect(jsonPath("$.message", is(MessageCode.GET_LOCATION_SUCCESS.getMessage())));

    verifyTokenIntrospectRequest();
  }

  @AfterEach
  public void cleanUp() {
    testDataHelper.getSiteRepository().deleteAll();
    testDataHelper.getStudyRepository().deleteAll();
    testDataHelper.getAppRepository().deleteAll();
    testDataHelper.getLocationRepository().deleteAll();
    testDataHelper.getUserRegAdminRepository().deleteAll();
  }

  private LocationRequest getLocationRequest() throws JsonProcessingException {
    return new LocationRequest(CUSTOM_ID_VALUE, LOCATION_NAME_VALUE, LOCATION_DESCRIPTION_VALUE);
  }

  private UpdateLocationRequest getUpdateLocationRequest() throws JsonProcessingException {
    UpdateLocationRequest updateLocationRequest = new UpdateLocationRequest();
    updateLocationRequest.setName(UPDATE_LOCATION_NAME_VALUE);
    updateLocationRequest.setDescription(UPDATE_LOCATION_DESCRIPTION_VALUE);
    return updateLocationRequest;
  }
}

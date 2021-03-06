package com.servicenow.demo.service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.servicenow.demo.Enumeration.AssignmentGroupType;
import com.servicenow.demo.Enumeration.IncidentStatusType;
import com.servicenow.demo.Enumeration.ServiceNowOperatorType;
import com.servicenow.demo.constants.Constants;
import com.servicenow.demo.entity.IncidentEntity;
import com.servicenow.demo.entity.ResponseSlaConfigEntity;
import com.servicenow.demo.repository.IncidentRepository;
import com.servicenow.demo.repository.ResponseSlaRepository;
import com.servicenow.demo.util.Utils;

@Service
public class IncidentService {

	private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

	@Value(value = "${jobs.incident.frequency}")
	private String incidentJobFrequency;

	@Value(value = "${incidents.api.url}")
	private String url;

	@Value(value = "${api.username}")
	private String username;

	@Value(value = "${api.password}")
	private String password;

	@Autowired
	ResponseSlaRepository responseSlaRepository;

	@Autowired
	IncidentRepository incidentRepository;

	@Scheduled(cron = "${jobs.incident.frequency}")
	public void getIncidents() throws ClientProtocolException, IOException, URISyntaxException, JSONException, ParseException {
		
		log.info("running Get-Incident Job at : " + Instant.now());
		String filterQuery = prepareFilterQuery();
		log.info("filter-query : {} ", filterQuery);

		URIBuilder builder = new URIBuilder(url);
		builder.setParameter("sysparm_query", filterQuery);

		HttpGet request = new HttpGet(builder.build());
		HttpClient httpClient = HttpClientBuilder.create().build();

		request.addHeader("accept", "application/json");
		request.addHeader("Content-Type", "application/json");
		request.addHeader("Authorization", "Basic UlBBU2VydmljZTpScGFAMjAyMA==");

		HttpResponse response = httpClient.execute(request);
		HttpEntity httpEntity = response.getEntity();
		
		String apiResponse = EntityUtils.toString(httpEntity);
		log.info("ServiceNow-API-Response: {} ",apiResponse);
		JSONObject responseJson = new JSONObject(apiResponse);
		JSONArray incidentJsonArray = (JSONArray) responseJson.get("result");
		List<IncidentEntity> incidents = prepareIncidents(incidentJsonArray);
		
		incidentRepository.saveAll(incidents);
		log.info("Successfully saved Incidents {} ",incidents);
		log.info("<<Get-Incident Job finished>>");
	}
	
	

	private List<IncidentEntity> prepareIncidents(JSONArray incidentJsonArray) throws ParseException, JsonMappingException, JSONException, JsonProcessingException {
		List<IncidentEntity> incidents = new ArrayList<IncidentEntity>();
		for (int i = 0; i < incidentJsonArray.length(); i++) {
			JSONObject incidentJson = (JSONObject) incidentJsonArray.get(i);
			incidents.add(toEntity(incidentJson)) ;
		}
		return incidents;
	}

	private IncidentEntity toEntity(JSONObject incidentJson) throws JSONException, ParseException, JsonMappingException, JsonProcessingException {
		IncidentEntity entity = new IncidentEntity();
		entity.setIncidentNumber(incidentJson.getString("number"));
		String assignedToJson =  incidentJson.get("assigned_to").toString();
		String assignedGroupJson =  incidentJson.get("assignment_group").toString();
		entity.setAssignedTo(Utils.getValue(assignedToJson));
		entity.setAssignmentGroup(Utils.getValue(assignedGroupJson));
		entity.setSubject(incidentJson.getString("short_description"));
		entity.setPriority(incidentJson.getString("priority"));
		entity.setSeverity(incidentJson.getString("severity"));
		entity.setStatus(IncidentStatusType.getById(incidentJson.getInt("state")).getName());
		entity.setCreatedOn(Utils.convertToDate(incidentJson.getString("sys_created_on")));
		entity.setUpdatedOn(Utils.convertToDate(incidentJson.getString("sys_updated_on")));
		return entity;
	}
	
	private String prepareFilterQuery() {
		Map<String, String> dateTimeMap = Utils.currentDateTimeMap();
		return new StringBuilder(Constants.ASSIGNMENT_GROUP).append("=")
				.append(AssignmentGroupType.CHAT_BOT_GROUP.getId()).append(ServiceNowOperatorType.OR.getId())
				.append(Constants.ASSIGNMENT_GROUP).append("=").append(AssignmentGroupType.RPA_GROUP.getId())
				.append(ServiceNowOperatorType.AND.getId()).append(Constants.SYS_UPDATED_ON).append(">=")
				.append("'"+dateTimeMap.get("date")+"',"+"'"+dateTimeMap.get("time")+"'").toString();
				//.append("'2020-09-09','12:12:12'").toString();

	}

	public List<ResponseSlaConfigEntity> findResponseSlaConig() {
		return responseSlaRepository.findAll();
	}
	
	

}


package org.sunbird;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextBookCorrection {

    private static Config conf = ConfigFactory.load();
    private static final String BASE_URL= conf.hasPath("lp_base_url")?conf.getString("lp_base_url"):"localhost:8080/learning-service";
    private static final String BEARER_KEY= conf.hasPath("bearer_key")?conf.getString("bearer_key"):"";
    private static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println(args);
        List<String> idList = Arrays.asList(args[1].split(","));

        if(null != idList && !idList.isEmpty()) {
            for(String tbId: idList){
                List<Map<String, Object>> children = getChildren(tbId);
                Map<String, Object> hierarchy = new HashMap<String, Object>();
                leadSir(tbId, children, hierarchy);
                updateHierarchy(tbId, hierarchy);
                System.out.println("Updated hierarchy for :: "+ tbId);
            }
        } else {
            System.out.println("idList is empty");
        }
    }

    private static void updateHierarchy(String tbId, final Map<String, Object> hierarchy) throws Exception {
        if(MapUtils.isNotEmpty(hierarchy)){
            Map<String, Object> requestBody = new HashMap<String, Object>() {{
               put("nodesModified", new HashMap<String, Object>());
               put("hierarchy", hierarchy);
            }};
            HttpResponse<String> updateHierarchyResp = Unirest.post(BASE_URL + "/content/v3/hierarchy/update").header("Content-Type", "application/json").header("authorization", BEARER_KEY).body(mapper.writeValueAsString(requestBody)).asString();
            if(200 != updateHierarchyResp.getStatus()){
                System.out.println("Update Hierarchy failed for :: "+ tbId + " :: " + updateHierarchyResp.getBody());
            }
        }
    }

    private static void leadSir(String tbId, List<Map<String, Object>> children, Map<String, Object> hierarchy) {
        if (CollectionUtils.isNotEmpty(children)) {
            List<String> listChildren = new ArrayList<String>();
            Map<String, Object> temp = new HashMap<String, Object>();
            for (Object child : children) {
                Map<String, Object> childMap = (Map<String, Object>) child;
                if (MapUtils.isNotEmpty(childMap)) {
                    if (StringUtils.equalsIgnoreCase("Parent", (String) childMap.get("visibility"))) {
                        listChildren.add((String) childMap.get("identifier"));
                        leadSir((String) childMap.get("identifier"), (List<Map<String, Object>>) childMap.get("children"), hierarchy);
                    } else if (StringUtils.equalsIgnoreCase("Default", (String) childMap.get("visibility"))) {
                        if (StringUtils.equalsIgnoreCase("application/vnd.ekstep.content-collection", (String) childMap.get("mimeType"))) {
                            listChildren.add((String) childMap.get("identifier"));
                        } else if (!StringUtils.equalsIgnoreCase("application/vnd.ekstep.content-collection", (String) childMap.get("mimeType")) &&
                                !StringUtils.equalsIgnoreCase("PracticeQuestionSet", (String) childMap.get("contentType"))) {
                            listChildren.add((String) childMap.get("identifier"));
                        } else if (!StringUtils.equalsIgnoreCase("application/vnd.ekstep.content-collection", (String) childMap.get("mimeType")) &&
                                StringUtils.equalsIgnoreCase("PracticeQuestionSet", (String) childMap.get("contentType"))) {
                            List<String> questionCategoryList = (List<String>) childMap.get("questionCategories");
                            String questionCategory = questionCategoryList.get(0);
                            if (temp.containsKey(questionCategory)) {
                                Map<String, Object> tempVal = (Map<String, Object>) temp.get(questionCategory);
                                if (((String) tempVal.get("lastUpdatedOn")).compareTo((String) childMap.get("lastUpdatedOn")) < 0) {
                                    //listChildren.remove((String)tempVal.get("identifier"));
                                    //listChildren
                                    listChildren.set(listChildren.indexOf((String) tempVal.get("identifier")), (String) childMap.get("identifier"));
                                    tempVal = new HashMap<String, Object>();
                                    tempVal.put("identifier", (String) childMap.get("identifier"));
                                    tempVal.put("lastUpdatedOn", (String) childMap.get("lastUpdatedOn"));
                                    temp.put(questionCategory, tempVal);
                                }
                            } else {
                                Map<String, Object> tempVal = new HashMap<String,Object>();
                                tempVal.put("identifier", (String) childMap.get("identifier"));
                                tempVal.put("lastUpdatedOn", (String) childMap.get("lastUpdatedOn"));
                                temp.put(questionCategory, tempVal);
                                listChildren.add((String) childMap.get("identifier"));
                            }
                        }
                    }
                }
            }
            hierarchy.put(tbId, listChildren);
        }
    }

    private static List<Map<String, Object>> getChildren(String tbId) throws UnirestException, IOException {
        HttpResponse<String> getHierarchyResp = Unirest.get(BASE_URL + "/content/v3/hierarchy/" + tbId + "?mode=edit").header("Content-Type", "application/json").header("authorization", BEARER_KEY).asString();
        if(200 == getHierarchyResp.getStatus()) {
            Response response = mapper.readValue(getHierarchyResp.getBody(), Response.class);
            return (List<Map<String, Object>>)((Map<String, Object>) response.get("content")).get("children");
        } else {
            System.out.println("Get Hierarchy response failed for :: " + tbId + " :: " + getHierarchyResp.getBody());
        }
        return null;
    }
}

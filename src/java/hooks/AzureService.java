package hooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Scenario;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.poi.ss.usermodel.Row;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class AzureService {

    private static final String CREATE_PLAN_PATH = "/test/plans?api-version=5.0";
    private static final String CREATE_TEST_SUITE_PATH = "/test/Plans/%s/suites/%s?api-version=5.0";
    private static final String CREATE_TEST_RUN_PATH = "/test/runs?api-version=5.0";
    private static final String GET_POINT_IDS_PATH = "/test/Plans/%s/Suites/%s/points?api-version=5.0";
    private static final String UPDATE_RUN_WITH_RESULT_PATH = "/test/Runs/%s/results?api-version=5.0";
    private static final String PAT = System.getProperty("AZURE_PAT");

    private static final Logger LOGGER = Logger.getLogger(AzureService.class.getName());
    private final String AZURE_URL;
    private final Map azureConfig;


    public AzureService() {
        this.azureConfig = loadAzureConfig();
        LOGGER.info("AZURE CONFIG: " + azureConfig);
        this.AZURE_URL = (String) azureConfig.get("ServerUrl");
    }

    public Map getAzureConfig() {
        return this.azureConfig;
    }

    public String[] createTestPlan() {
        try {
            String planName = (String) azureConfig.get("PlanName");
            String planAreaPath = (String) azureConfig.get("PlanAreaPath");
            String planIterationPath = (String) azureConfig.get("PlanIterationPath");
            LOGGER.info("Create plan with name: " + planName + " via API at Iteration: " + planIterationPath + " and Area path: " + planAreaPath);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + getEncodedPat());
            headers.put("Accept", "application/json");
            headers.put("Content-type", "application/json");

            JSONObject body = new JSONObject();
            body.put("name", planName);
            body.put("iteration", planIterationPath);
            JSONObject areaPath = new JSONObject();
            areaPath.put("name", planAreaPath);
            body.put("area", areaPath);

            Unirest.config().verifySsl(false);
            HttpResponse<JsonNode> apiResponse = Unirest.post(AZURE_URL + CREATE_PLAN_PATH)
                    .headers(headers).body(body).asJson();
            LOGGER.info("Create Plan API response: " + apiResponse.getBody());
            LOGGER.info("Create Plan API status: " + apiResponse.getStatus());
            LOGGER.info("Create Plan API status text: " + apiResponse.getStatusText());

            JSONObject responseBody = apiResponse.getBody().getObject();
            String planId = String.valueOf(responseBody.getInt("id"));
            String rootSuiteId = String.valueOf(responseBody.getJSONObject("rootSuite").getInt("id"));

            LOGGER.info(String.format("Response of Create Plan API: '%s': \n\n%s", planName, responseBody + "\n" + apiResponse.getStatus() + " :: " + apiResponse.getStatusText()));

            return new String[]{planId, rootSuiteId};
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to Create Plan: via API", e);
        }
    }

    public String createTestSuite(String planId, String rootSuiteId) {
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();
            String suiteName = azureConfig.get("SuiteName") + "_" + dtf.format(now);
            LOGGER.info("Create Suite based on Query: Test cases with FE Automation Status as: 'Done and SIT/EAT Automated', with name: " + suiteName + " via API under test plan: " + planId);

            String queryString = "SELECT [System.Id],[System.WorkItemType],[System.Title],[Microsoft.VSTS.Common.Priority],[System.AssignedTo],[System.AreaPath] FROM WorkItems WHERE [System.TeamProject] = @project AND [System.WorkItemType] IN GROUP 'Microsoft.TestCaseCategory' AND [Jio.Common.FEAutomationStatus] IN ('EAT and SIT Automated')";

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + getEncodedPat());
            headers.put("Accept", "application/json");
            headers.put("Content-type", "application/json");

            JSONObject body = new JSONObject();
            body.put("name", suiteName);
            body.put("suiteType", "DynamicTestSuite");
            body.put("queryString", queryString);

            Unirest.config().verifySsl(false);
            HttpResponse<JsonNode> apiResponse = Unirest.post(AZURE_URL + getCreateTestSuiteAPIPath(planId, rootSuiteId))
                    .headers(headers).body(body).asJson();
            LOGGER.info("Create Test Suite API response: " + apiResponse.getBody());
            LOGGER.info("Create Test Suite API status: " + apiResponse.getStatus());

            JSONObject responseBody = apiResponse.getBody().getObject();

            String suiteId = String.valueOf(responseBody.getJSONArray("value").getJSONObject(0).getInt("id"));

            LOGGER.info(String.format("Response of Create Test Suite API: '%s': \n\n%s", suiteName, responseBody + "\n" + apiResponse.getStatus() + " :: " + apiResponse.getStatusText()));
            return suiteId;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to Create Test Suite: via API", e);
        }
    }

    public String createTestRun(int[] pointIDs, String planId) {
        try {
            String runName = (String) azureConfig.get("RunName");
            LOGGER.info("Create Test Run with name: " + runName + " via API under test plan: " + planId);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + getEncodedPat());
            headers.put("Accept", "application/json");
            headers.put("Content-type", "application/json");

            JSONObject body = new JSONObject();
            body.put("name", runName);
            body.put("pointIds", pointIDs);
            JSONObject plan = new JSONObject();
            plan.put("id", planId);
            body.put("plan", plan);

            Unirest.config().verifySsl(false);
            HttpResponse<JsonNode> apiResponse = Unirest.post(AZURE_URL + CREATE_TEST_RUN_PATH)
                    .headers(headers).body(body).asJson();
            LOGGER.info("Create Test Run API response: " + apiResponse.getBody());
            LOGGER.info("Create Test Run API status: " + apiResponse.getStatus());

            JSONObject responseBody = apiResponse.getBody().getObject();

            String runId = String.valueOf(responseBody.getInt("id"));
            LOGGER.info("RUN Created with ID: " + runId);
            LOGGER.info(String.format("Response of Create Test Run API: '%s': \n\n%s", runName, responseBody + "\n" + apiResponse.getStatus() + " :: " + apiResponse.getStatusText()));
            return runId;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to Create Run: via API", e);
        }
    }

    public Object[] getPointIDs(String planId, String suiteId) {
        try {
            List<Integer> pointIDs = new ArrayList<>();
            HashMap<String, Integer> testCaseIDs = new HashMap<>();
            LOGGER.info("Get Point IDs for current Suite");

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + getEncodedPat());

            Unirest.config().verifySsl(false);
            HttpResponse<JsonNode> apiResponse = Unirest.get(AZURE_URL + getPointIDsAPIPath(planId, suiteId))
                    .headers(headers).asJson();
            LOGGER.info("Get PointIDs API response: " + apiResponse.getBody());
            LOGGER.info("Get PointIDs API status: " + apiResponse.getStatus());

            JSONObject responseBody = apiResponse.getBody().getObject();
            JSONArray valueArr = responseBody.getJSONArray("value");

            for (int i = 0; i < valueArr.length(); ++i) {
                int pointId = valueArr.getJSONObject(i).getInt("id");
                pointIDs.add(pointId);
                testCaseIDs.put(String.valueOf(valueArr.getJSONObject(i).getJSONObject("testCase").getInt("id")), pointId);
            }

            LOGGER.info("PointIDs For current run: " + pointIDs);
            LOGGER.info("Test Cases For current run: " + testCaseIDs);
            LOGGER.info(String.format("Response of Get PointIDs API: \n\n%s", responseBody + "\n" + apiResponse.getStatus() + " :: " + apiResponse.getStatusText()));

            return new Object[]{pointIDs.stream().mapToInt(i -> i).toArray(), testCaseIDs};
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to Get PointIDs for current run via API", e);
        }
    }

    public AzureService uploadTestResults(String planId, String suiteId) {
        LOGGER.info("Uploading test results");
        Object[] pointDetails = getPointIDs(planId, suiteId);
        int[] pointIDs = (int[]) pointDetails[0];
        HashMap<String, Integer> allTestCaseIDs = (HashMap<String, Integer>) pointDetails[1];

        // Get Json String of results of all the scenarios executed
        String resultJson = getResultDataJson(pointIDs, allTestCaseIDs);

        // Create test run if run ID not provided
        String runId = System.getProperty("RUN_ID");
        if (runId == null || runId.isEmpty()) {
            runId = createTestRun(pointIDs, planId);
        }

        try {
            String runName = (String) azureConfig.get("RunName");
            LOGGER.info("Update Test Run with name: " + runName + " via API under test plan: " + planId);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Basic " + getEncodedPat());
            headers.put("Accept", "application/json");
            headers.put("Content-type", "application/json");

            Unirest.config().verifySsl(false);
            HttpResponse<JsonNode> apiResponse = Unirest.patch(AZURE_URL + getUpdateRunAPIPath(runId))
                    .headers(headers).body(resultJson).asJson();

            LOGGER.info(String.format("Response of Update Run API: '%s': \n\n%s", runName, apiResponse.getBody() + "\n" + apiResponse.getStatus() + " :: " + apiResponse.getStatusText()));
            return this;
        } catch (UnirestException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to Update the results in run: " + runId + " via API", e);
        }
    }

    public String extractTestCaseIds(Scenario scenario) {
        LOGGER.info("Extracting Test case IDs from scenario");
        StringBuilder tcIDs = new StringBuilder();
        for (String tag : scenario.getSourceTagNames()) {
            if (tag.contains("TC-") || tag.contains("TC_")) {
                tcIDs.append(tag.split("-")[1]).append(",");
            }
        }
        return tcIDs.toString();
    }

    private String getEncodedPat() {
        LOGGER.info("Encode PAT for authentication");
        String AuthStr = ":" + PAT;
        Base64 base64 = new Base64();
        return new String(base64.encode(AuthStr.getBytes()));
    }

    private String getResultDataJson(int[] pointIDs, HashMap<String, Integer> allTestCaseIDs) {
        LOGGER.info("Get Result Json");
        LOGGER.info("Map point IDs and result output IDs");
        HashMap<Integer, HashMap> allPointData = getMappedPointIDs(pointIDs);

        LOGGER.info("Read test data from Test Run Excel (AutomationTestRun) and Update result map with test data");

        // Sceanrio data is added to excel sheet after every scenario and then after suit test cases are marked by reading data from excel
//        List<Row> testData = new ExcelUtil().getTestRunDataFromExcel();
        List<Row> testData = new ArrayList<>();
        for (int i = 1; i < testData.size(); i++) {
            // One row has data of one scenario, with columns:
            // 0: "Description of Scenario", 1: "Status", 2: "Test Case Ids", 3: "Feature"
            Row currentData = testData.get(i);
            String[] tcIDs = currentData.getCell(2).toString().split(",");
            for (String tcID : tcIDs) {
                // if Test case ID mentioned in scenario is present in Run, then update the result map with the data
                if (allTestCaseIDs.containsKey(tcID)) {
                    int pointId = allTestCaseIDs.get(tcID);
                    HashMap pointData = allPointData.get(pointId);
                    pointData.put("state", "Completed");
                    pointData.put("outcome", currentData.getCell(1).toString());
                    pointData.put("comment", "Test Case run by Automation: " + tcID + " : " + currentData.getCell(1) +
                            " | Feature File: " + currentData.getCell(3) +
                            " | Description: " + currentData.getCell(0) +
                            " | Reports portal link: " + SessionContext.getReportPortalLaunchURL()
                    );
                }

            }
        }

        LOGGER.info("Create result array in same order of the test cases present in run");
        return createResultJson(pointIDs, allPointData);
    }

    private String getCreateTestSuiteAPIPath(String planId, String rootSuiteId) {
        return String.format(CREATE_TEST_SUITE_PATH, planId, rootSuiteId);
    }

    private String getPointIDsAPIPath(String planId, String suiteId) {
        return String.format(GET_POINT_IDS_PATH, planId, suiteId);
    }

    private String getUpdateRunAPIPath(String runId) {
        return String.format(UPDATE_RUN_WITH_RESULT_PATH, runId);
    }

    private Map loadAzureConfig() {
        LOGGER.info("Load Azure config");
        try {
            return new ObjectMapper().readValue(Paths.get("./resources/configuration/azure_config.json").toFile(), Map.class);
        } catch (IOException e) {
            LOGGER.info("Azure config loading failure: \n" + e);
            throw new RuntimeException("Error loading Azure config", e);
        }
    }

    private HashMap<Integer, HashMap> getMappedPointIDs(int[] pointIDs) {
        HashMap<Integer, HashMap> allPointData = new HashMap<>();
        for (int i = 0; i < pointIDs.length; ++i) {
            HashMap<String, Object> pointData = new HashMap<>();
            pointData.put("id", 100000 + i);
            pointData.put("state", "");
            pointData.put("outcome", "");
            pointData.put("comment", "Test Case run by Automation");
            allPointData.put(pointIDs[i], pointData);
        }
        return allPointData;
    }

    private String createResultJson(int[] pointIDs, HashMap<Integer, HashMap> allPointData) {
        JSONArray result = new JSONArray();
        for (int pointId : pointIDs) {
            HashMap pointData = allPointData.get(pointId);
            result.put(new JSONObject(pointData));
        }
        String resultJson = result.toString();
        LOGGER.info("Final Result Json: \n" + resultJson);
        return resultJson;
    }
}

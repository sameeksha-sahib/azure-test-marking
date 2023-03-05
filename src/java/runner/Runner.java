package runner;

import hooks.AzureService;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import org.junit.After;
import org.junit.Before;
import org.testng.annotations.DataProvider;
import utils.ExcelUtil;
import java.util.logging.Logger;


public class Runner extends AbstractTestNGCucumberTests {
    private static final Logger LOGGER = Logger.getLogger(Runner.class.getName());
    private static boolean beforeSuite = false;
    private long scenarioStartTime;

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        Object[][] scenarios = super.scenarios();
        LOGGER.info(scenarios);
        return scenarios;
    }

    @Before
    public void before(Scenario scenario) {
        LOGGER.info("****** beforeScenario");
        scenarioStartTime = System.currentTimeMillis();

        if (!beforeSuite) {
            LOGGER.info("****** beforeSuite");
            new ExcelUtil().createTestExcelBeforeSuit();
            LOGGER.info("Initializing after suite hook, based on Java shutdown event");
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    LOGGER.info("****** afterSuite");
                    try {
                        LOGGER.info("****** Mark test case status");
                        AzureService azureService = new AzureService();
                        String[] planDetails = createTestPlanIfIDNotProvided(azureService);
                        String planId = planDetails[0];
                        String rootSuiteId = planDetails[1];
                        String suiteId = createTestSuiteIfIDNotProvided(azureService, planId, rootSuiteId);
                        LOGGER.info("****** Upload test results");
                        azureService.uploadTestResults(planId, suiteId);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        LOGGER.info("Unable to mark tests in Azure");
                    }
                }
            });
            beforeSuite = true;
        }
    }

    @After
    public void afterScenario(Scenario scenario) {
        LOGGER.info("****** afterScenario");
        long scenarioExecutionTimeSec = (System.currentTimeMillis() - scenarioStartTime) / 1000;
        new ExcelUtil().writeTestDataExcelAfterScenario(scenario, scenarioExecutionTimeSec);
    }

    private String[] createTestPlanIfIDNotProvided(AzureService azureService) {
        String planId = System.getProperty("PLAN_ID");
        String rootSuiteId = System.getProperty("ROOT_SUITE_ID");

        LOGGER.info("****** Create test plan if plan ID not provided");
        if (planId == null || planId.isEmpty()) {
            return azureService.createTestPlan();
        }

        return new String[]{planId, rootSuiteId};
    }

    private String createTestSuiteIfIDNotProvided(AzureService azureService, String planId, String rootSuiteId) {
        LOGGER.info("****** Create test suite if suite ID not provided");
        String suiteId = System.getProperty("SUITE_ID");
        if (suiteId == null || suiteId.isEmpty()) {
            if (rootSuiteId == null || rootSuiteId.isEmpty()) {
                throw new RuntimeException("Error Creating Test Suite: Please provide Root Suite ID");
            }
            suiteId = azureService.createTestSuite(planId, rootSuiteId);
        }
        return suiteId;
    }
}

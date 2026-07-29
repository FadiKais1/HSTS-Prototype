package hsts.server.net;

import hsts.common.Request;
import hsts.common.RequestType;
import hsts.common.Response;
import hsts.server.control.AuthService;
import hsts.server.control.ExamManagementService;
import hsts.server.repository.QuestionRepository;
import hsts.server.support.InMemoryUserRepository;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PrincipalRouteContractTest {
    @Test
    public void everyPrincipalRouteRequiresAuthenticationWhenContextFree() {
        Server server = new Server(
                0, new ExamManagementService(new QuestionRepository()),
                new AuthService(new InMemoryUserRepository())
        );
        List<RequestType> routes = List.of(
                RequestType.LIST_ALL_QUESTIONS,
                RequestType.LIST_QUESTION_VERSIONS_FOR_PRINCIPAL,
                RequestType.GET_QUESTION_VERSION_FOR_PRINCIPAL,
                RequestType.LIST_ALL_EXAMS,
                RequestType.LIST_EXAM_VERSIONS_FOR_PRINCIPAL,
                RequestType.GET_EXAM_VERSION_FOR_PRINCIPAL,
                RequestType.LIST_ALL_EXECUTIONS,
                RequestType.LIST_EXECUTION_RESULTS_FOR_PRINCIPAL,
                RequestType.GET_SUBMISSION_RESULT_FOR_PRINCIPAL
        );
        for (RequestType route : routes) {
            Response response = server.handleRequest(new Request(route, null));
            assertFalse(route.name(), response.isSuccess());
            assertEquals(route.name(), "Authentication context required",
                    response.getMessage());
        }
    }
}

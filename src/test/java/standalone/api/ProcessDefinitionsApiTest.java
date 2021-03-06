package standalone.api;

import feign.Feign;
import feign.gson.GsonDecoder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import standalone.feign.FeignConfig;
import standalone.feign.api.ProcessDefinitionsApi;
import standalone.feign.api.Results;
import standalone.feign.api.entity.ProcessDefinitionsEntityCustom;
import standalone.feign.decoder.StatusDecoder;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@Import({FeignConfig.class})
public class ProcessDefinitionsApiTest {

    private final String url = "http://localhost:8080/activiti-rest/service";

    @Autowired
    private Feign.Builder builder;
    @Autowired
    private Feign.Builder statusBuilder;

    private ProcessDefinitionsApi processDefinitions;
    private ProcessDefinitionsApi statusProcessDefinitions;

    @PostConstruct
    private void init(){
        processDefinitions = builder.decoder(new GsonDecoder()).target(ProcessDefinitionsApi.class, url);
        statusProcessDefinitions = statusBuilder.decoder(new StatusDecoder()).target(ProcessDefinitionsApi.class, url);
    }

    @Test
    public void listProcessDefinitions() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "审核退回");
//        map.put("deploymentId", "515018");
//        map.put("category", "_");
        Results<ProcessDefinitionsEntityCustom> pds = processDefinitions.listProcessDefinitions(map);
        System.out.println(pds);
    }

    @Test
    public void getProcessDefinition() {
        ProcessDefinitionsEntityCustom pd = processDefinitions.getProcessDefinition("myProcess:1:2504");
        System.out.println(pd);
    }

    @Test
    public void updateCategory() {
        Integer integer = statusProcessDefinitions.updateCategory("myProcess:1:2504","updateCategoryTest");
        System.out.println(integer);
    }
}
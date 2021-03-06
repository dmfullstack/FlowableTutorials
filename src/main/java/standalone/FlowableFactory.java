package standalone;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.flowable.engine.impl.test.AbstractTestCase.assertEquals;


/**
 * @author Adam
 */
@Slf4j
@Service
public class FlowableFactory {
    /**
     * 流程引擎
     */
    @Autowired
    private ProcessEngine processEngine;
    /**
     * 存储服务
     */
    @Autowired
    private RepositoryService repositoryService;
    /**
     * 运行时服务
     */
    @Autowired
    private RuntimeService runtimeService;
    /**
     * 任务服务
     */
    @Autowired
    private TaskService taskService;
    /**
     * 管理服务
     */
    @Autowired
    private ManagementService managementService;

    /**
     * 部署流程
     *
     * @param file
     * @return
     */
    public List<ProcessDefinition> deploy(String... file) {
        if (file == null) {
            throw new RuntimeException("文件不能为空！");
        }
        DeploymentBuilder builder = repositoryService.createDeployment();
        for (String bpmn : file) {
            builder.addClasspathResource(bpmn);
            builder.name(bpmn);
        }
        // 关闭语法错误检查 ( DTD格式检查 )
        // builder.disableSchemaValidation();
        // 关闭流程错误验证 ( 流程图画的不对 如 流程冲突 )
        // builder.disableBpmnValidation();
        // 部署
        Deployment dep = builder.deploy();
        log.debug("{} 部署成功！", Arrays.toString(file));
        return repositoryService.createProcessDefinitionQuery()
                .deploymentId(dep.getId()).latestVersion().list();
    }

    /**
     * 批量部署压缩文件
     *
     * @param zipInputStream
     * @return
     */
    public Deployment deploy(ZipInputStream... zipInputStream) {
        DeploymentBuilder deployment = repositoryService.createDeployment();
        Arrays.stream(zipInputStream).forEach(deployment::addZipInputStream);
        return deployment.deploy();
    }

    /**
     * 部署并启动一个流程
     * @param bpmn20Xml
     * @return
     */
    public ProcessInstance deployAndStart(Map<String, Object> param, String... bpmn20Xml) {

        List<ProcessDefinition> processDefinitions = this.deploy(bpmn20Xml);
        ProcessInstance processInstance = null;
        for (ProcessDefinition p : processDefinitions) {
            // 启动流程
            processInstance = runtimeService.startProcessInstanceById(p.getId(), p.getKey(), param);
            log.debug("流程实例[{}] BusinessKey[{}] 已启动 ...", processInstance, processInstance.getBusinessKey());

        }
        log.debug("返回的流程实例[{},{}]", processInstance, processInstance.getBusinessKey());
        return processInstance;
    }

    public ProcessInstance deployAndStart(String... bpmn20Xml) {
        return this.deployAndStart(null, bpmn20Xml);
    }


    public void complete(ProcessInstance pi) {
        complete(pi, null);
    }

    public void complete(ProcessInstance pi, Map<String, Object> params) {
        if (pi == null) {
            throw new RuntimeException("流程实例不能为空！");
        }

        log.debug("当前流程实例id：" + pi.getId() + ", BusinessKey:" + pi.getBusinessKey() + "正在获取任务 ...");

        List<Task> tasks = taskService.createTaskQuery().processInstanceId(pi.getId()).list();

        Print.tasks(tasks);
        for (Task task : tasks) {
            taskService.complete(task.getId(), params);
            log.debug("当前任务：[{}] 任务已完成！", task);
            List<Task> tasks2 = taskService.createTaskQuery().processInstanceId(pi.getId()).list();
            for (Task t : tasks2) {
                log.debug("当前最新任务：[{}]", t);
            }
        }

    }


    final String GEN_PATH = "src/main/resources/generated/";

    /**
     * 构建BPMN模型
     *
     * @param fileName  不需要扩展名
     * @param bpmnModel
     * @param png
     * @throws IOException
     */
    public Deployment buildBpmn(String fileName, BpmnModel bpmnModel, String processKey, boolean png) throws Exception {

        //act_ge_bytearray 表中的NAME_
        String bpmnFileName = fileName + ".bpmn20.xml";
        String pngFileName = fileName + ".png";

        // 把BpmnModel对象部署到引擎
        DeploymentBuilder builder = repositoryService.createDeployment();
        Deployment deployment = builder.addBpmnModel(bpmnFileName, bpmnModel).name("Dynamic process deployment")
                .deploy();

        // 启动流程
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(processKey);

        // 检查流程是否正常启动
        List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
        assertEquals(true, tasks.size() > 0);

        String deployID = deployment.getId();
        InputStream deploymentResource = repositoryService.getResourceAsStream(deployID, bpmnFileName);


        // 把文件生成在本章项目的generated目录中
        String userHomeDir = "src/main/resources/generated/";

        // 导出Bpmn20.xml文件到本地文件系统
        InputStream processBpmn = repositoryService.getResourceAsStream(deployment.getId(), bpmnFileName);
        File xmlFile = new File(userHomeDir + bpmnFileName);
        FileUtils.copyInputStreamToFile(processBpmn, xmlFile);

        log.debug("流程文件" + xmlFile.getAbsolutePath() + " 写入完成！");
        deploymentResource.close();
        if (png) {
            // 导出流程图
            InputStream processDiagram = repositoryService.getProcessDiagram(processInstance.getProcessDefinitionId());
            File pngFile = new File(userHomeDir + pngFileName);
            FileUtils.copyInputStreamToFile(processDiagram, pngFile);
            log.debug("流程图片" + pngFile.getAbsolutePath() + " 写入完成！");
            processDiagram.close();
        }
        return deployment;
    }

    private void writePng(String fileName, InputStream pngResource) {
        BufferedImage bufferedImage = null;
        try {
            File file = new File(GEN_PATH + fileName + ".png");
            if (!file.exists()) {
                file.createNewFile();
            }
            bufferedImage = ImageIO.read(pngResource);
            FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(bufferedImage, "png", fos);
            fos.close();
            log.debug("流程图片" + file.getAbsolutePath() + " 写入完成！");
        } catch (IOException e) {
            log.error("流程图片写入失败！" + e.getMessage());
        }
    }

    /**
     * 快速由xml文件生成png
     *
     * @param deployFileName
     */
    public void generatePng(String deployFileName) {
        Deployment dep = repositoryService.createDeployment().addClasspathResource(deployFileName).deploy();
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery().deploymentId(dep.getId()).latestVersion().singleResult();
        InputStream processDiagram = repositoryService.getProcessDiagram(pd.getId());
        writePng(deployFileName, processDiagram);
    }

    public List<Task> listTask(String processInstanceID) {
        TaskQuery taskQuery = taskService.createTaskQuery();
        taskQuery.processInstanceId(processInstanceID);
        return taskQuery.list();
    }

    public InputStream getResourceAsStream(String deploymentId, String resourceName) {
        return repositoryService.getResourceAsStream(deploymentId, resourceName);
    }

    public void suspendProcessDefinitionById(String processDefinitionID) {
        repositoryService.suspendProcessDefinitionById(processDefinitionID);
    }

    public ProcessInstance startProcessInstanceById(String processDefinitionID) {
        return runtimeService.startProcessInstanceById(processDefinitionID);
    }

    /**
     * 获取子流程列表
     *
     * @param processInstanceID
     * @return
     */
    public List<Execution> listChildExecution(String processInstanceID) {
        ExecutionQuery executionQuery = runtimeService.createExecutionQuery()
                .processInstanceId(processInstanceID)
                .onlyChildExecutions();
        return executionQuery.list();
    }

    public void signalEventReceived(String signalName) {
        runtimeService.signalEventReceived(signalName);
    }

    public void messageEventReceived(String messageName, String executionId) {
        runtimeService.messageEventReceived(messageName, executionId);
    }

    public void trigger(String executionId) {
        runtimeService.trigger(executionId);
    }

    public void suspendProcessInstanceById(String processInstanceID) {
        runtimeService.suspendProcessInstanceById(processInstanceID);
    }

    public void activateProcessInstanceById(String processInstanceID) {
        runtimeService.activateProcessInstanceById(processInstanceID);
    }
    public boolean isAsyncExecutorActivate(){
        return processEngine.getProcessEngineConfiguration().isAsyncExecutorActivate();
    }

    public boolean isAsyncHistoryExecutorActivate(){
        return processEngine.getProcessEngineConfiguration().isAsyncHistoryExecutorActivate();
    }
}

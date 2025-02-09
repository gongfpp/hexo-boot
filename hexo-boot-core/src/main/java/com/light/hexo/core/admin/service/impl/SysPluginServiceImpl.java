package com.light.hexo.core.admin.service.impl;

import cn.hutool.core.util.ZipUtil;
import com.light.hexo.common.base.BaseRequest;
import com.light.hexo.common.base.BaseServiceImpl;
import com.light.hexo.common.constant.HexoExceptionEnum;
import com.light.hexo.common.exception.GlobalException;
import com.light.hexo.common.plugin.BasePlugin;
import com.light.hexo.common.plugin.HexoBootPluginManager;
import com.light.hexo.common.plugin.registry.AbstractModuleRegistry;
import com.light.hexo.common.request.PluginRequest;
import com.light.hexo.common.util.DateUtil;
import com.light.hexo.common.util.ExceptionUtil;
import com.light.hexo.core.admin.service.SysPluginService;
import com.light.hexo.mapper.base.BaseMapper;
import com.light.hexo.mapper.mapper.SysPluginMapper;
import com.light.hexo.mapper.model.SysPlugin;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.util.FileUtil;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @Author MoonlightL
 * @ClassName: SysPluginServiceImpl
 * @ProjectName hexo-boot
 * @Description: 插件 Service 实现
 * @DateTime 2022/4/13, 0013 10:34
 */
@Service
@Slf4j
public class SysPluginServiceImpl extends BaseServiceImpl<SysPlugin> implements SysPluginService {

    @Autowired
    private SysPluginMapper pluginMapper;

    @Autowired
    private HexoBootPluginManager pluginManager;

    @Override
    public BaseMapper<SysPlugin> getBaseMapper() {
        return this.pluginMapper;
    }

    @Override
    protected Example getExample(BaseRequest request) {
        PluginRequest pluginRequest = (PluginRequest) request;
        Example example = new Example(SysPlugin.class);

        Example.Criteria criteria = example.createCriteria();

        String pluginId = pluginRequest.getPluginId();
        if (StringUtils.isNotBlank(pluginId)) {
            criteria.andLike("pluginId", pluginId.trim() + "%");
        }

        return example;
    }

    @Override
    public int removeModel(Serializable id) throws GlobalException {
        return super.removeModel(id);
    }

    @Override
    public String installPlugin(String originalFilename, InputStream inputStream) throws GlobalException {

        String originName = FilenameUtils.getBaseName(originalFilename);
        SysPlugin dbPlugin = this.getPluginByName(originName);
        if (dbPlugin != null) {
            ExceptionUtil.throwEx(HexoExceptionEnum.ERROR_PLUGIN_INSTALLED);
        }

        String tmpPath = System.getProperty("java.io.tmpdir");
        File tmpDir = new File(tmpPath, "pluginDir");
        if (tmpDir.exists()) {
            FileUtils.deleteQuietly(tmpDir);
        }

        File unzipPluginCog = ZipUtil.unzip(inputStream, tmpDir, Charset.defaultCharset());
        File jarFile = new File(unzipPluginCog.getAbsolutePath(), originName + ".jar");
        if (!jarFile.exists()) {
            FileUtils.deleteQuietly(tmpDir);
            ExceptionUtil.throwEx(HexoExceptionEnum.ERROR_PLUGIN_INVALID);
        }

        Path pluginsRoot = this.pluginManager.getPluginsRoot();
        String parentDirPath = pluginsRoot.toString();
        File newPluginFile = new File(parentDirPath, jarFile.getName());

        try {
            FileUtil.copyFile(jarFile, newPluginFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String filePath = newPluginFile.getAbsolutePath();
        String pluginId = this.pluginManager.loadPlugin(Paths.get(filePath));
        PluginWrapper pluginWrapper = this.pluginManager.getPlugin(pluginId);
        PluginDescriptor descriptor = pluginWrapper.getDescriptor();

        PluginState pluginState = this.pluginManager.startPlugin(pluginId, filePath);
        String configUrl = ((BasePlugin) pluginWrapper.getPlugin()).getConfigUrl();
        SysPlugin plugin = new SysPlugin();
        plugin.setPluginId(pluginId)
              .setOriginName(originName)
              .setState(pluginState.toString().equals(PluginState.STARTED.toString()))
              .setRemark(descriptor.getPluginDescription())
              .setVersion(descriptor.getVersion())
              .setAuthor(descriptor.getProvider())
              .setFilePath(filePath)
              .setConfigUrl(StringUtils.isBlank(configUrl) ? "" : configUrl)
              .setCreateTime(LocalDateTime.now())
              .setUpdateTime(plugin.getCreateTime());
        this.pluginMapper.insert(plugin);

        return pluginId;
    }

    @Override
    public void updatePlugin(SysPlugin plugin) throws GlobalException {

        SysPlugin dbPlugin = super.findById(plugin.getId());
        if (dbPlugin == null) {
            ExceptionUtil.throwEx(HexoExceptionEnum.ERROR_PLUGIN_NOT_EXIST);
        }

        if (dbPlugin.getState().equals(plugin.getState())) {
            return;
        }

        String pluginId = dbPlugin.getPluginId();

        if (plugin.getState()) {
            this.pluginManager.startPlugin(pluginId, dbPlugin.getFilePath());
        } else {
            this.pluginManager.stopPlugin(pluginId);
        }

        super.updateModel(plugin);
    }

    @Override
    public void uninstallPlugin(Integer id) throws GlobalException {

        SysPlugin dbPlugin = super.findById(id);
        if (dbPlugin == null) {
            ExceptionUtil.throwEx(HexoExceptionEnum.ERROR_PLUGIN_NOT_EXIST);
        }

        String pluginId = dbPlugin.getPluginId();

        File pluginFile = new File(dbPlugin.getFilePath());
        if (pluginFile.exists()) {
            File parentFile = pluginFile.getParentFile();
            File bakFileDir = new File(parentFile.getParentFile().getAbsolutePath(), "plugins-bak");
            if (!bakFileDir.exists()) {
                bakFileDir.mkdirs();
            }

            try {
                String dateTimeStr = DateUtil.ldtToStr(LocalDateTime.now(), DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                File bakFile = new File(bakFileDir.getAbsolutePath(), FilenameUtils.getBaseName(pluginFile.getName()) + "-" + dateTimeStr + ".jar");
                FileUtils.copyFile(pluginFile, bakFile);
            } catch (IOException e) {
                log.error("=========== uninstallPlugin 备份失败 plugin-id: {} =================", pluginId);
            }

            try {
                if (this.pluginManager.checkPlugin(pluginId)) {
                    this.pluginManager.deletePlugin(pluginId);
                } else {
                    boolean deleteQuietly = false;
                    int tryCount = 0;
                    while (!deleteQuietly && tryCount++ < 10) {
                        System.gc();
                        deleteQuietly = FileUtils.deleteQuietly(pluginFile);
                    }

                    if (!deleteQuietly) {
                        this.deletePluginFileError(dbPlugin);
                    }
                }
                super.removeModel(id);
            } catch (Exception e) {
                log.error("=========== uninstallPlugin 删除插件失败 plugin-id: {}, error: {}=================", pluginId, e);
                this.deletePluginFileError(dbPlugin);
            } finally {
                System.gc();
            }
        }
    }

    @Override
    public boolean checkPlugin(String pluginId) throws GlobalException {
        Example example = new Example(SysPlugin.class);
        example.createCriteria().andEqualTo("pluginId", pluginId);
        SysPlugin dbPlugin = this.pluginMapper.selectOneByExample(example);
        return dbPlugin != null && dbPlugin.getState();
    }

    @Override
    public void clearCache() throws GlobalException {
        Path pluginsRoot = this.pluginManager.getPluginsRoot();
        String pluginDirPath = pluginsRoot.toString();
        File pluginDir = new File(pluginDirPath);
        if (!pluginDir.exists()) {
            return;
        }

        File[] files = pluginDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            String filename = file.getName();
            String originName = FilenameUtils.getBaseName(filename);
            SysPlugin dbPlugin = this.getPluginByName(originName);
            if (dbPlugin == null) {
                // 删除
                AbstractModuleRegistry.PLUGIN_CLASS_MAP.clear();
                FileUtils.deleteQuietly(file);
            }
        }
    }

    private void deletePluginFileError(SysPlugin sysPlugin) {
        sysPlugin.setState(false).setUpdateTime(LocalDateTime.now());
        this.updateModel(sysPlugin);
        ExceptionUtil.throwEx(HexoExceptionEnum.ERROR_PLUGIN_CANNOT_DELETE);
    }

    private SysPlugin getPluginByName(String name) {
        Example example = new Example(SysPlugin.class);
        example.createCriteria().andEqualTo("originName", name);
        return this.pluginMapper.selectOneByExample(example);
    }
}

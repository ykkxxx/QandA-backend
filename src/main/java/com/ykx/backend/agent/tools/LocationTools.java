package com.ykx.backend.agent.tools;

import com.ykx.backend.aiutil.AmapApiUtil;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationTools {
    @Resource
    private AmapApiUtil amapApiUtil;
    /**
     * 查询指定城市实时+预报天气
     * @param city 城市名称，如武汉、北京
     * @return 高德天气接口完整JSON
     */
    @Tool("查询指定城市的天气情况，city为城市名称，如'北京'、'上海'")
    public String getWeather(String city) {
        log.info("LLM工具调用：查询城市天气，city={}", city);
        return amapApiUtil.getWeather(city);
    }

    @Tool("搜索指定地址附近的场所，address为地址（支持学校、校区、详细地址），keywords为关键词（如景点、美食），radius为搜索半径（米）")
    public String searchPoiByAddress(String address, String keywords, Integer radius) {
        log.info("LLM工具调用：地址周边POI检索，address={}, keywords={}, radius={}", address, keywords, radius);
        return amapApiUtil.searchPoiByAddress(address, keywords, radius);
    }

}

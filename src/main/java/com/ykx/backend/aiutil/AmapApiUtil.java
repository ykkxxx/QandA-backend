package com.ykx.backend.aiutil;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ykx.backend.config.AmapConfigProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;


@Component
@Slf4j
public class AmapApiUtil {
    @Resource
    private AmapConfigProperties amapConfigProperties;

    /**
     * 根据城市查询天气
     *
     * @param city
     * @return
     */
    public String getWeather(String city) {
        if (StrUtil.isBlank(city)) {
            log.warn("天气查询参数异常：城市名称为空");
            return "{\"status\":\"0\",\"info\":\"城市名称不能为空\"}";
        }
        try {
            String baseUrl = amapConfigProperties.getBaseUrl() + "/v3/weather/weatherInfo";
            String param = String.format("key=%s&city=%s&extensions=all", amapConfigProperties.getApiKey(), city);
            String result = HttpUtil.get(baseUrl + "?" + param);
            log.info("天气查询请求完成，city={}", city);
            return result;
        } catch (Exception e) {
            log.error("天气接口调用异常，city={}", city, e);
            return "{\"status\":\"0\",\"info\":\"调用天气服务异常\"}";
        }
    }

    // 原有地理编码
    public String geoCode(String address) {
        if (StrUtil.isBlank(address)) {
            log.warn("地理编码参数异常：地址为空");
            return "{\"status\":\"0\",\"info\":\"地址不能为空\"}";
        }
        try {
            String baseUrl = amapConfigProperties.getBaseUrl() + "/v3/geocode/geo";
            String param = String.format("key=%s&address=%s", amapConfigProperties.getApiKey(), address);
            String result = HttpUtil.get(baseUrl + "?" + param);
            log.info("地理编码请求完成，address={}", address);
            return result;
        } catch (Exception e) {
            log.error("地理编码接口调用异常，address={}", address, e);
            return "{\"status\":\"0\",\"info\":\"地址解析服务调用异常\"}";
        }
    }

    // 新增：支持学校/校区/详细地址，周边检索POI
    // 精确地址周边POI检索（修复后无报错）
    public String searchPoiByAddress(String address, String keywords, Integer radius) {
        if (StrUtil.isBlank(address) || StrUtil.isBlank(keywords)) {
            return "{\"status\":\"0\",\"info\":\"地址、搜索关键词不能为空\"}";
        }
        try {
            // 1. 地址转坐标
            String geoResp = geoCode(address);
            JSONObject geoResult = JSONUtil.parseObj(geoResp);

            if (!"1".equals(String.valueOf(geoResult.get("status")))) {
                return "{\"status\":\"0\",\"info\":\"无法解析该地址坐标，请更换地点重试\"}";
            }
            List<Map> geocodes = geoResult.getBeanList("geocodes", Map.class);
            String location = geocodes.get(0).get("location").toString();

            // 2. 周边检索接口
            int searchRadius = radius == null ? 3000 : Math.min(radius, 5000);
            String aroundUrl = amapConfigProperties.getBaseUrl() + "/v3/place/around";
            String param = String.format(
                    "key=%s&location=%s&keywords=%s&radius=%d",
                    amapConfigProperties.getApiKey(),
                    location,
                    keywords,
                    searchRadius
            );
            String poiResult = HttpUtil.get(aroundUrl + "?" + param);
            log.info("精确地址周边POI检索 address={}, keywords={}", address, keywords);
            return poiResult;
        } catch (Exception e) {
            log.error("地址周边POI检索失败 address={}, keywords={}", address, keywords, e);
            return "{\"status\":\"0\",\"info\":\"周边场所查询异常\"}";
        }

    }
}

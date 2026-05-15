package ai.bookmind.service;

import ai.bookmind.annotation.LogOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 外部工具 API 服务
 * - 天气查询: Open-Meteo (免费，无需Key)
 * - 新闻查询: Saurav's NewsAPI (免费，无需Key)
 */
@Service
@Slf4j
public class ToolApiService {

    private final RestTemplate restTemplate;

    @Autowired
    private McpClientService mcpClientService;

    public ToolApiService() {
        this.restTemplate = new RestTemplate();
    }

    // 常见中国城市坐标
    private static final Map<String, double[]> CITY_COORDS = Map.ofEntries(
        Map.entry("北京", new double[]{39.9042, 116.4074}),
        Map.entry("上海", new double[]{31.2304, 121.4737}),
        Map.entry("广州", new double[]{23.1291, 113.2644}),
        Map.entry("深圳", new double[]{22.5431, 114.0579}),
        Map.entry("杭州", new double[]{30.2741, 120.1551}),
        Map.entry("成都", new double[]{30.5728, 104.0668}),
        Map.entry("武汉", new double[]{30.5928, 114.3055}),
        Map.entry("南京", new double[]{32.0603, 118.7969}),
        Map.entry("重庆", new double[]{29.4316, 106.9123}),
        Map.entry("西安", new double[]{34.3416, 108.9398}),
        Map.entry("长沙", new double[]{28.2282, 112.9388}),
        Map.entry("天津", new double[]{39.1252, 117.1908}),
        Map.entry("苏州", new double[]{31.2990, 120.5853}),
        Map.entry("郑州", new double[]{34.7466, 113.6253}),
        Map.entry("青岛", new double[]{36.0671, 120.3826}),
        Map.entry("大连", new double[]{38.9140, 121.6147}),
        Map.entry("昆明", new double[]{25.0389, 102.7183}),
        Map.entry("厦门", new double[]{24.4798, 118.0894}),
        Map.entry("哈尔滨", new double[]{45.8038, 126.5350}),
        Map.entry("拉萨", new double[]{29.6500, 91.1000}),
        Map.entry("香港", new double[]{22.3193, 114.1694}),
        Map.entry("台北", new double[]{25.0330, 121.5654})
    );

    private static final Map<String, String> COUNTRY_MAP = Map.ofEntries(
        Map.entry("中国", "cn"), Map.entry("美国", "us"), Map.entry("英国", "gb"),
        Map.entry("印度", "in"), Map.entry("加拿大", "ca"), Map.entry("日本", "jp"),
        Map.entry("韩国", "kr"), Map.entry("法国", "fr"), Map.entry("德国", "de"),
        Map.entry("澳大利亚", "au"), Map.entry("俄罗斯", "ru")
    );

    // 省份→省会映射（天气查询用）
    private static final Map<String, String> PROVINCE_TO_CITY = Map.ofEntries(
        Map.entry("新疆", "乌鲁木齐"), Map.entry("海南", "海口"),
        Map.entry("广东", "广州"), Map.entry("浙江", "杭州"),
        Map.entry("江苏", "南京"), Map.entry("福建", "福州"),
        Map.entry("山东", "济南"), Map.entry("四川", "成都"),
        Map.entry("湖北", "武汉"), Map.entry("湖南", "长沙"),
        Map.entry("河南", "郑州"), Map.entry("河北", "石家庄"),
        Map.entry("陕西", "西安"), Map.entry("山西", "太原"),
        Map.entry("辽宁", "沈阳"), Map.entry("吉林", "长春"),
        Map.entry("黑龙江", "哈尔滨"), Map.entry("安徽", "合肥"),
        Map.entry("江西", "南昌"), Map.entry("广西", "南宁"),
        Map.entry("云南", "昆明"), Map.entry("贵州", "贵阳"),
        Map.entry("甘肃", "兰州"), Map.entry("内蒙古", "呼和浩特"),
        Map.entry("西藏", "拉萨"), Map.entry("宁夏", "银川"),
        Map.entry("青海", "西宁"), Map.entry("台湾", "台北")
    );

    private static final Map<String, String> CATEGORY_MAP = Map.of(
        "科技", "technology", "商业", "business", "娱乐", "entertainment",
        "体育", "sports", "健康", "health", "科学", "science", "综合", "general"
    );

    /**
     * 检测消息是否为天气/新闻查询，执行对应API调用，返回格式化上下文文本
     * 如果不是天气/新闻查询，返回 null
     */
    public String executeToolCall(String message) {
        if (message == null || message.isBlank()) return null;

        String weatherResult = tryWeatherQuery(message.trim());
        if (weatherResult != null && !weatherResult.isBlank()) return weatherResult;

        String newsResult = tryNewsQuery(message.trim());
        if (newsResult != null && !newsResult.isBlank()) return newsResult;

        return null;
    }

    // ==================== 天气查询 ====================

    private String tryWeatherQuery(String message) {
        if (!containsAny(message, "天气", "气温", "温度", "下雨", "下雪", "刮风",
                "weather", "temperature", "forecast")) {
            return null;
        }

        // 检测所有匹配的城市/省份
        List<String> cities = detectAllCities(message);
        if (cities.isEmpty()) {
            return queryWeather(39.9042, 116.4074, "当前位置");
        }
        if (cities.size() == 1) {
            double[] coords = getCityCoords(cities.get(0));
            if (coords != null) return queryWeather(coords[0], coords[1], cities.get(0));
            return queryWeather(39.9042, 116.4074, cities.get(0));
        }
        // 多个城市→对比查询
        StringBuilder sb = new StringBuilder();
        sb.append("【🌤 多地天气对比】\n");
        for (String city : cities) {
            double[] coords = getCityCoords(city);
            if (coords != null) {
                String one = queryWeather(coords[0], coords[1], city);
                if (one != null && !one.isBlank()) sb.append("\n--- ").append(city).append(" ---\n").append(one);
            }
        }
        String r = sb.toString().trim();
        return r.endsWith("【🌤 多地天气对比】") ? null : r;  // 全部失败则返回null
    }

    @LogOperation("天气查询")
    public String queryWeather(double lat, double lon, String cityName) {
        try {
            String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=Asia/Shanghai",
                lat, lon
            );

            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result == null) return null;

            StringBuilder sb = new StringBuilder();
            sb.append("【🌤 天气预报 - ").append(cityName).append("】\n");

            Map<String, Object> current = (Map<String, Object>) result.get("current_weather");
            if (current != null) {
                double temp = (double) current.get("temperature");
                double wind = (double) current.get("windspeed");
                int code = ((Number) current.get("weathercode")).intValue();
                sb.append("当前温度：").append(temp).append("°C\n");
                sb.append("天气状况：").append(weatherCodeToText(code)).append("\n");
                sb.append("风速：").append(wind).append(" km/h\n");
            }

            Map<String, Object> daily = (Map<String, Object>) result.get("daily");
            if (daily != null) {
                List<Double> maxTemps = (List<Double>) daily.get("temperature_2m_max");
                List<Double> minTemps = (List<Double>) daily.get("temperature_2m_min");
                List<Integer> weatherCodes = (List<Integer>) daily.get("weathercode");
                if (maxTemps != null && !maxTemps.isEmpty()) {
                    sb.append("\n未来预报：\n");
                    String[] labels = {"今天", "明天", "后天"};
                    for (int i = 0; i < Math.min(3, maxTemps.size()); i++) {
                        String day = i < labels.length ? labels[i] : "第" + (i + 1) + "天";
                        sb.append("  ").append(day).append("：");
                        if (weatherCodes != null && i < weatherCodes.size()) {
                            sb.append(weatherCodeToText(weatherCodes.get(i))).append(" ");
                        }
                        sb.append(minTemps.get(i)).append("~").append(maxTemps.get(i)).append("°C\n");
                    }
                }
            }
            sb.append("(数据来源：Open-Meteo)");

            return sb.toString();
        } catch (Exception e) {
            log.warn("天气查询失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 新闻查询 ====================

    private String tryNewsQuery(String message) {
        if (!containsAny(message, "新闻", "头条", "资讯", "news", "headline", " headlines")) {
            return null;
        }

        String country = "us";
        String countryName = "美国";

        for (Map.Entry<String, String> e : COUNTRY_MAP.entrySet()) {
            if (message.contains(e.getKey())) {
                country = e.getValue();
                countryName = e.getKey();
                break;
            }
        }

        String category = "general";
        for (Map.Entry<String, String> e : CATEGORY_MAP.entrySet()) {
            if (message.contains(e.getKey())) {
                category = e.getValue();
                break;
            }
        }

        return queryNews(category, country, countryName);
    }

    public String queryNews(String category, String country, String countryName) {
        try {
            String categoryCn = CATEGORY_MAP.entrySet().stream()
                .filter(e -> e.getValue().equals(category))
                .map(Map.Entry::getKey)
                .findFirst().orElse(category);

            // 用 MCP web search 搜实时新闻
            String query = String.format("最新%s新闻 %s", categoryCn, countryName);
            String searchResult = mcpClientService.searchWeb(query, 8);

            if (searchResult == null || searchResult.isEmpty() || searchResult.contains("不可用")) {
                return "【📰 " + countryName + " " + categoryCn + "新闻】(暂时无法获取最新新闻)";
            }

            return "【📰 " + countryName + " " + categoryCn + "新闻头条】\n" + searchResult
                    + "\n(数据来源：open-websearch)";
        } catch (Exception e) {
            log.warn("新闻查询失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 辅助 ====================

    /** 判断是否支持该城市 */
    public boolean hasCity(String city) {
        return CITY_COORDS.containsKey(city);
    }

    /** 获取城市坐标（列表优先 → 地理编码兜底） */
    public double[] getCityCoords(String city) {
        double[] coords = CITY_COORDS.get(city);
        if (coords != null) return coords;
        return geocode(city);
    }

    /** Open-Meteo Geocoding API（免费，支持任何城市） */
    private double[] geocode(String city) {
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8)
                    + "&count=1&language=zh&format=json";
            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result == null) return null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
            if (results != null && !results.isEmpty()) {
                Map<String, Object> first = results.get(0);
                double lat = ((Number) first.get("latitude")).doubleValue();
                double lon = ((Number) first.get("longitude")).doubleValue();
                log.info("地理编码: {} → ({}, {})", city, lat, lon);
                return new double[]{lat, lon};
            }
        } catch (Exception e) {
            log.warn("地理编码失败 {}: {}", city, e.getMessage());
        }
        return null;
    }

    /** 根据国家代码获取中文名称 */
    public String getCountryName(String countryCode) {
        return COUNTRY_MAP.entrySet().stream()
                .filter(e -> e.getValue().equals(countryCode))
                .map(Map.Entry::getKey)
                .findFirst().orElse(countryCode);
    }

    /** 检测单个城市（用于省份→省会映射后匹配坐标） */
    private String detectCity(String message) {
        List<String> all = detectAllCities(message);
        return all.isEmpty() ? null : all.get(0);
    }

    /** 检测消息中所有匹配的城市/省份名称 */
    private List<String> detectAllCities(String message) {
        List<String> result = new java.util.ArrayList<>();
        // 先匹配已知城市
        for (String city : CITY_COORDS.keySet()) {
            if (message.contains(city)) result.add(city);
        }
        // 再匹配省份→省会（去重）
        for (Map.Entry<String, String> e : PROVINCE_TO_CITY.entrySet()) {
            if (message.contains(e.getKey())) {
                String mapped = e.getValue();
                if (!result.contains(mapped)) result.add(mapped);
            }
        }
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private String weatherCodeToText(int code) {
        if (code == 0) return "☀️ 晴";
        if (code <= 2) return "⛅ 多云";
        if (code == 3) return "☁️ 阴";
        if (code <= 10) return "🌫️ 雾/霾";
        if (code <= 20) return "🌧️ 雨";
        if (code <= 30) return "🌨️ 雪";
        if (code <= 50) return "🌧️ 阵雨/毛毛雨";
        if (code <= 60) return "🌧️ 中雨";
        if (code <= 70) return "🌨️ 雪";
        if (code <= 80) return "🌦️ 阵雨";
        if (code <= 90) return "🌩️ 雷暴";
        return "🌈 未知";
    }
}

package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.config.ProviderConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final ProviderConfig providerConfig;

    /** 获取当前 AI 提供商 */
    @GetMapping("/provider")
    public Result<Map<String, String>> getProvider() {
        return Result.success(Map.of("provider", providerConfig.getChatProvider()));
    }

    /** 热切换 AI 提供商（siliconflow | sensenova，无需重启） */
    @PutMapping("/provider")
    public Result<Map<String, String>> setProvider(@RequestParam String provider) {
        providerConfig.setChatProvider(provider);
        return Result.success(Map.of("provider", providerConfig.getChatProvider()));
    }
}

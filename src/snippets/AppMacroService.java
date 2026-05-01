// @source src/main/java/com/samsung/move/agent/service/AppMacroService.java
// @note CRUD + duplicate (이름 + "(복사)") — Service 패턴 단순
// @synced 2026-05-01T01:10:31.193Z

package com.samsung.move.agent.service;

import com.samsung.move.agent.entity.AppMacro;
import com.samsung.move.agent.repository.AppMacroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppMacroService {

    private final AppMacroRepository repository;

    public List<AppMacro> findAll() {
        return repository.findAll();
    }

    public AppMacro findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("App macro not found: " + id));
    }

    public AppMacro create(AppMacro macro) {
        return repository.save(macro);
    }

    public AppMacro update(Long id, AppMacro data) {
        AppMacro existing = findById(id);
        existing.setName(data.getName());
        existing.setDescription(data.getDescription());
        existing.setPackageName(data.getPackageName());
        existing.setEventsJson(data.getEventsJson());
        existing.setDeviceWidth(data.getDeviceWidth());
        existing.setDeviceHeight(data.getDeviceHeight());
        return repository.save(existing);
    }

    public AppMacro duplicate(Long id) {
        AppMacro src = findById(id);
        AppMacro copy = AppMacro.builder()
                .name(src.getName() + " (복사)")
                .description(src.getDescription())
                .packageName(src.getPackageName())
                .eventsJson(src.getEventsJson())
                .deviceWidth(src.getDeviceWidth())
                .deviceHeight(src.getDeviceHeight())
                .build();
        return repository.save(copy);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}


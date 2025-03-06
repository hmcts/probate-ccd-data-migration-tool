package uk.gov.hmcts.reform.migration.service.dtspb4583;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
class Dtspb4583DataServiceImpl implements Dtspb4583DataService {
    private final LoaderService loaderService;

    private final AtomicReference<Map<Long, Dtspb4583Dates>> cacheMap;
    private final Object mapLock;

    public Dtspb4583DataServiceImpl(
        final LoaderService loaderService
    ) {
        this.loaderService = loaderService;
        this.cacheMap = new AtomicReference<>();
        this.mapLock = new Object();
    }

    Map<Long, Dtspb4583Dates> getAll() {
        final Map<Long, Dtspb4583Dates> cache = cacheMap.get();
        if (cache != null) {
            return cache;
        }
        synchronized (mapLock) {
            final Map<Long, Dtspb4583Dates> cacheInner = cacheMap.get();
            if (cacheInner != null) {
                log.info("Data already loaded inside lock, provide from cache");
                return cacheInner;
            }

            log.info("Data not loaded");
            final Map<Long, Dtspb4583Dates> loadedMap = loaderService.load();
            cacheMap.set(loadedMap);
            log.info("Data loaded");
            return loadedMap;
        }
    }

    @Override
    public Optional<Dtspb4583Dates> get(final Long id) {
        final Map<Long, Dtspb4583Dates> map = getAll();
        if (map.containsKey(id)) {
            return Optional.of(map.get(id));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String getReferences() {
        final Map<Long, Dtspb4583Dates> map = getAll();
        return String.join(
                ",",
                map.keySet().stream()
                    .sorted()
                    .map(Object::toString)
                    .toList());
    }
}

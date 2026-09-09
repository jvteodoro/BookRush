package com.bookrush.ingestion.source;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetDiscoveryService {
  private final AssetRepresentationSelector selector = new AssetRepresentationSelector();
  public List<AssetRepresentationSelector.Representation> discover(List<AssetRepresentationSelector.Representation> candidates, int limit) {
    return selector.select(candidates, limit);
  }
}

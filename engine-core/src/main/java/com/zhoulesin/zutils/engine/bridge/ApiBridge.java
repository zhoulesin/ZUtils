package com.zhoulesin.zutils.engine.bridge;

import java.util.List;

public interface ApiBridge {
    String callApi(String apiTag, List<String> params);
}

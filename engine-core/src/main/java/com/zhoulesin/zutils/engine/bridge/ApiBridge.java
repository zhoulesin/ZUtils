package com.zhoulesin.zutils.engine.bridge;

import java.util.List;

public interface ApiBridge {
    Object callApi(String apiTag, List<String> params);
}

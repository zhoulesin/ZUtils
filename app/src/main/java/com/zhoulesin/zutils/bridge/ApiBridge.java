package com.zhoulesin.zutils.bridge;

import java.util.List;

public interface ApiBridge {
    String callApi(String apiTag, List<String> params);
}

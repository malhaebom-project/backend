package com.malhaebom.malhaebom.service.policy;

import java.time.Duration;

public record SpeechShutdownPolicy(Duration drainTimeout) {}

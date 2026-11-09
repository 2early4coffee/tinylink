package com._early4coffee.tinylink.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitData implements Serializable {

    private static final long serialVersionUID = 1L;

    private int minuteCount;
    private int hourCount;

    //10:00
    private LocalDateTime minuteWindowStart;
    private LocalDateTime hourWindowStart;
}
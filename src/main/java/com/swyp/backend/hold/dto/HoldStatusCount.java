package com.swyp.backend.hold.dto;

import com.swyp.backend.hold.entity.HoldCanceledBy;
import com.swyp.backend.hold.entity.HoldStatus;

public record HoldStatusCount(HoldStatus status, HoldCanceledBy canceledBy, long count) {
}

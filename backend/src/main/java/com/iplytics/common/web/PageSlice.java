package com.iplytics.common.web;

import java.util.List;

/** One page of rows plus the total number of matching rows (from a separate count query). */
public record PageSlice<T>(List<T> items, long total) {
}

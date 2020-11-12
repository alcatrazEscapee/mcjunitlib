package com.alcatrazescapee.mcjunitlib.framework;

import java.util.List;

final class TestResult
{
    final List<String> errors;
    final boolean pass;

    TestResult(List<String> errors, boolean pass)
    {
        this.errors = errors;
        this.pass = pass;
    }
}

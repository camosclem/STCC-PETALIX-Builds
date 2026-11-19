package com.petalix.guidefirst;

import java.util.ArrayList;
import java.util.List;

final class Channel {
    final String key, name, group, epg, stream;
    final List<Program> programs = new ArrayList<>();

    Channel(String key, String name, String group, String epg, String stream) {
        this.key = key;
        this.name = name;
        this.group = group;
        this.epg = epg;
        this.stream = stream;
    }

    Program at(long time) {
        for (Program program : programs) {
            if (program.start <= time && time < program.end) return program;
            if (program.start > time) break;
        }
        return null;
    }

    static final class Program {
        final long start, end;
        final String title, description;

        Program(long start, long end, String title, String description) {
            this.start = start;
            this.end = end;
            this.title = title;
            this.description = description;
        }
    }
}

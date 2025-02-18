package com.search.sample;

import dagger.Component;

@Component(modules = AppModule.class)
interface DaggerAppComponent {
    void inject(SearchLambdaHandler handler);
}
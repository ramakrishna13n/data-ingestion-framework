package com.search.sample;

@dagger.Module
class AppModule {
    @dagger.Provides
    RedshiftQueryService provideRedshiftQueryService() {
        return new RedshiftQueryService();
    }
}

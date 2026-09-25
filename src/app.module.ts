import { Module } from '@nestjs/common';
import { APP_FILTER, APP_GUARD } from '@nestjs/core';
import { BusModule } from './bus/bus.module';
import { GatewayKeyGuard } from './common/gateway-key.guard';
import { HttpExceptionFilter } from './common/http-exception.filter';
import { RolesGuard } from './common/identity';
import { ExampleModule } from './example/example.module';
import { HealthModule } from './health/health.module';

@Module({
  imports: [
    HealthModule,
    BusModule,
    // Módulo de ejemplo: elimínalo cuando arranques tu servicio real.
    ExampleModule,
  ],
  providers: [
    // Orden de los guards: primero se comprueba que la petición venga del
    // gateway y después el rol del usuario.
    { provide: APP_GUARD, useClass: GatewayKeyGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
    { provide: APP_FILTER, useClass: HttpExceptionFilter },
  ],
})
export class AppModule {}

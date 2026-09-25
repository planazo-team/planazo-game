// Controlador de EJEMPLO. Muestra cómo usar @Identity(), @Roles() y BusService.
// Bórralo (toda la carpeta src/example y su import en app.module.ts) cuando
// arranques tu servicio real.
import { Controller, Get, HttpCode, Post } from '@nestjs/common';
import { BusService } from '../bus/bus.service';
import { Identity, Roles } from '../common/identity';

@Controller('example')
export class ExampleController {
  constructor(private readonly bus: BusService) {}

  /** Devuelve la identidad que el gateway puso en los headers. */
  @Get('whoami')
  whoami(@Identity() identidad: Identity) {
    return identidad;
  }

  /** Solo negocios: publica un evento de prueba al bus. */
  @Roles('negocio')
  @Post('emit')
  @HttpCode(202)
  async emit(@Identity() identidad: Identity) {
    const payload = { from: identidad.userId, placeId: identidad.placeId ?? null };
    await this.bus.publish('EXAMPLE.PING', payload, ['zone:zona-g']);
    return { ok: true, type: 'EXAMPLE.PING', topics: ['zone:zona-g'], payload };
  }
}

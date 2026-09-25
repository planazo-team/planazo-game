// Módulo de EJEMPLO: bórralo junto con el resto de src/example.
import { Module } from '@nestjs/common';
import { ExampleController } from './example.controller';

@Module({
  controllers: [ExampleController],
})
export class ExampleModule {}

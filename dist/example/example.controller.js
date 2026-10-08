"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ExampleController = void 0;
const common_1 = require("@nestjs/common");
const bus_service_1 = require("../bus/bus.service");
const identity_1 = require("../common/identity");
let ExampleController = class ExampleController {
    bus;
    constructor(bus) {
        this.bus = bus;
    }
    whoami(identidad) {
        return identidad;
    }
    async emit(identidad) {
        const payload = { from: identidad.userId, placeId: identidad.placeId ?? null };
        await this.bus.publish('EXAMPLE.PING', payload, ['zone:zona-g']);
        return { ok: true, type: 'EXAMPLE.PING', topics: ['zone:zona-g'], payload };
    }
};
exports.ExampleController = ExampleController;
__decorate([
    (0, common_1.Get)('whoami'),
    __param(0, (0, identity_1.Identity)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", void 0)
], ExampleController.prototype, "whoami", null);
__decorate([
    (0, identity_1.Roles)('negocio'),
    (0, common_1.Post)('emit'),
    (0, common_1.HttpCode)(202),
    __param(0, (0, identity_1.Identity)()),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [Object]),
    __metadata("design:returntype", Promise)
], ExampleController.prototype, "emit", null);
exports.ExampleController = ExampleController = __decorate([
    (0, common_1.Controller)('example'),
    __metadata("design:paramtypes", [bus_service_1.BusService])
], ExampleController);
//# sourceMappingURL=example.controller.js.map
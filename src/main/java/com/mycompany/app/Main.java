package com.mycompany.app;

import com.hashicorp.cdktf.App;


public class Main
{
    public static void main(String[] args) throws Exception {
        final App app = new App();
        new BastionStack(app, "bastion-dev-stack", "dev");
        new BastionStack(app, "bastion-prod-stack", "prod");
        app.synth();
    }
}
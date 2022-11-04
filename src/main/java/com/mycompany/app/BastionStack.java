package com.mycompany.app;

import com.hashicorp.cdktf.TerraformHclModule;
import com.hashicorp.cdktf.TerraformOutput;
import com.hashicorp.cdktf.providers.aws.instance.Instance;
import com.hashicorp.cdktf.providers.aws.provider.AwsProvider;
import com.hashicorp.cdktf.providers.aws.security_group.SecurityGroup;
import com.hashicorp.cdktf.providers.aws.security_group.SecurityGroupEgress;
import com.hashicorp.cdktf.providers.aws.security_group.SecurityGroupIngress;
import software.constructs.Construct;

import com.hashicorp.cdktf.TerraformStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BastionStack extends TerraformStack
{
    public BastionStack(final Construct scope, final String id, String environment) throws Exception {
        super(scope, id);

        switch(environment){
            case "dev":
                AwsProvider.Builder.create(this, "AWS")
                        .region("us-east-1")
                        .profile("admingen")
                        .build();
                break;
            case "prod":
                AwsProvider.Builder.create(this, "AWS")
                        .region("us-east-1")
                        .profile("adminprod")
                        .build();
                break;
            default:
                throw new Exception("Not a valid environment");
        }

        String[] availabilityZones = {"us-east-1a"};
        String[] privateSubnetCidr = {"10.0.0.0/27"};
        String[] publicSubnetCidr = {"10.0.0.32/27"};

        HashMap<String, Object> vpcModuleVariables = new HashMap<>();
        vpcModuleVariables.put("name", "sandbox");
        vpcModuleVariables.put("cidr","10.0.0.0/26");
        vpcModuleVariables.put("azs", availabilityZones);
        vpcModuleVariables.put("private_subnets", privateSubnetCidr);
        vpcModuleVariables.put("public_subnets", publicSubnetCidr);
        vpcModuleVariables.put("enable_dns_hostnames", true);
        vpcModuleVariables.put("enable_dns_support", true);

        TerraformHclModule terraformHclModuleSampleVpc = TerraformHclModule.Builder.create(this, "isolated_vpc")
                .source("terraform-aws-modules/vpc/aws")
                .variables(vpcModuleVariables)
                .build();

        ArrayList<String> myIpCidrBlocks = new ArrayList<>();
        ArrayList<String> egressCidrBlocks = new ArrayList<>();
        myIpCidrBlocks.add(System.getenv("MY_IP_ADDRESS"));
        egressCidrBlocks.add("0.0.0.0/0");

        SecurityGroupIngress bastionSecurityGroupIngress = SecurityGroupIngress.builder()
                .fromPort(22)
                .toPort(22)
                .cidrBlocks(myIpCidrBlocks)
                .protocol("tcp")
                .description("allow ssh from my IP")
                .build();

        SecurityGroupEgress securityGroupEgress = SecurityGroupEgress.builder()
                .fromPort(0)
                .toPort(0)
                .protocol("-1")
                .cidrBlocks(egressCidrBlocks)
                .build();

        ArrayList<SecurityGroupIngress> bastionSgIngressList = new ArrayList<>();
        ArrayList<SecurityGroupEgress> sgEngressList = new ArrayList<>();
        bastionSgIngressList.add(bastionSecurityGroupIngress);
        sgEngressList.add(securityGroupEgress);

        SecurityGroup bastionSecurityGroup = SecurityGroup.Builder.create(this, "bastion_sg")
                .description("allow ssh from my IP")
                .ingress(bastionSgIngressList)
                .egress(sgEngressList)
                .name("bastion_sg")
                .vpcId("${module.isolated_vpc.vpc_id}").build();

        ArrayList<String> sGroupIdList = new ArrayList<>();
        sGroupIdList.add(bastionSecurityGroup.getId());

        SecurityGroupIngress privateBoxSecurityGroupIngress = SecurityGroupIngress.builder()
                .fromPort(22)
                .toPort(22)
                .protocol("tcp")
                .description("allow ssh from bastion host")
                .securityGroups(sGroupIdList)
                .build();

        ArrayList<SecurityGroupIngress> privateBoxSgIngList = new ArrayList<>();
        privateBoxSgIngList.add(privateBoxSecurityGroupIngress);

        SecurityGroup privateBoxSecurityGroup = SecurityGroup.Builder.create(this, "private_box_sg")
                .description("allow ssh from bastion host")
                .egress(sgEngressList)
                .ingress(privateBoxSgIngList)
                .name("private_box_sg")
                .vpcId("${module.isolated_vpc.vpc_id}").build();

        ArrayList<String> vpcBastionSecGroupIds = new ArrayList<>();
        ArrayList<String> vpcPrivateBoxSecGroupIds = new ArrayList<>();
        vpcBastionSecGroupIds.add(bastionSecurityGroup.getId());
        vpcPrivateBoxSecGroupIds.add(privateBoxSecurityGroup.getId());

        Instance bastionHost = Instance.Builder.create(this, "bastion_host_instance")
                .ami("ami-08c40ec9ead489470")
                .associatePublicIpAddress(true)
                .instanceType("t2.micro")
                .keyName("bastionkey"+environment)
                .vpcSecurityGroupIds(vpcBastionSecGroupIds)
                .tags(Map.of("Name", "bastion-host-"+environment))
                .subnetId("${module.isolated_vpc.public_subnets[0]}").build();

        Instance privateBox = Instance.Builder.create(this, "private_box_instance")
                .ami("ami-08c40ec9ead489470")
                .instanceType("t2.micro")
                .keyName("bastionkey"+environment)
                .vpcSecurityGroupIds(vpcPrivateBoxSecGroupIds)
                .tags(Map.of("Name", "private-box-"+environment))
                .subnetId("${module.isolated_vpc.private_subnets[0]}").build();

        TerraformOutput .Builder.create(this, "bastion_public_ip")
                .value(bastionHost.getPublicIp()).build();
        TerraformOutput.Builder.create(this, "box_private_ip")
                .value(privateBox.getPrivateIp()).build();
    }
}
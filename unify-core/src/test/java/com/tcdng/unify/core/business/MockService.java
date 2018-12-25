/*
 * Copyright 2018 The Code Department
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.tcdng.unify.core.business;

import java.util.List;

import com.tcdng.unify.core.UnifyException;

/**
 * Interface for business service used for tests.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public interface MockService extends BusinessService {

    String hello() throws UnifyException;

    int add(int a, int b) throws UnifyException;

    int multiply(int a, int b) throws UnifyException;

    Long createAccount(Account account) throws UnifyException;

    Account findAccount(Long accountId) throws UnifyException;

    List<Account> find(AccountQuery query) throws UnifyException;

    Long createLoanAccount(String accountNo, String accountName, Double amount) throws UnifyException;

    LoanAccount findLoanAccount(Long loanAccountId) throws UnifyException;
}